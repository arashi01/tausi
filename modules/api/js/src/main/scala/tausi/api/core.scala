/*
 * Copyright (c) 2025 Tausi contributors.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package tausi.api

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.scalajs.js
import scala.util.Try

import tausi.api.internal.*

/** Core Tauri API functions.
  *
  * This object contains the primary interface for interacting with the Tauri runtime from the
  * frontend. It provides type-safe command invocation using the Command typeclass system.
  *
  * Async functions return Future[T] with errors propagated through the Future's failure channel,
  * wrapped in TauriError. Designed for seamless integration with effect systems (cats-effect, ZIO).
  */
object core:
  /** Check if the code is running inside a Tauri application.
    *
    * @return true if running in Tauri, false otherwise
    */
  inline def isTauri: Boolean = isTauriGlobal

  /** Register a callback with the Tauri runtime and obtain a [[CallbackId]].
    *
    * Mirrors the upstream `transformCallback` helper for callbacks that may be invoked multiple
    * times.
    *
    * @param callback Function invoked with the callback payload
    */
  def transformCallback[T](callback: T => Unit): CallbackId =
    registerTransform(Some(callback), isOnce = false)

  /** Version of [[transformCallback]] that accepts an optional callback.
    *
    * This models the upstream ability to omit the callback altogether.
    */
  def transformCallbackOptional[T](callback: Option[T => Unit]): CallbackId =
    registerTransform(callback, isOnce = false)

  /** Register a callback that should be invoked at most once. */
  def transformCallbackOnce[T](callback: T => Unit): CallbackId =
    registerTransform(Some(callback), isOnce = true)

  /** Unregister a previously transformed callback. */
  def unregisterCallback(callbackId: CallbackId): Unit =
    TauriInternalsGlobal.unregisterCallback(callbackId.toInt)

  private def registerTransform[T](
    callback: Option[T => Unit],
    isOnce: Boolean
  ): CallbackId =
    val jsCallback: js.UndefOr[js.Function1[T, Unit]] =
      callback match
        case Some(fn) =>
          val jsFn: js.Function1[T, Unit] = (value: T) => fn(value)
          jsFn
        case None => js.undefined

    val rawId = TauriInternalsGlobal.transformCallback(jsCallback, isOnce)
    CallbackId.unsafe(rawId)
  end registerTransform

  /** Invoke a Tauri command with no parameters (zero-argument command).
    *
    * This is the primary way to call zero-argument Rust commands from the frontend. The command is
    * resolved from implicit scope using the Command0 typeclass.
    *
    * Errors are propagated through the Future's failure channel and wrapped in TauriError. This
    * includes both Rust Result::Err values and unexpected errors (serialization, IPC failures).
    *
    * @param cmd The Command0 instance (resolved implicitly)
    * @param ec Execution context for async operations
    * @tparam Res The expected return type
    * @return Future that succeeds with Res or fails with TauriError
    *
    * @example
    *   {{{
    * import tausi.api.core.*
    * import tausi.api.commands.app.{given, *}
    *
    * // Command0[String] resolved implicitly
    * val version: Future[String] = invoke
    *   }}}
    */
  def invoke[Res](using
    cmd: Command0[Res],
    ec: ExecutionContext
  ): Future[Res] =
    val jsPromise = TauriInternalsGlobal.invoke[js.Any](
      cmd.id.value,
      js.Dictionary.empty[js.Any],
      InvokeOptionsJS.empty
    )

    jsPromise.toFuture
      .flatMap: rawResponse =>
        Command.decodeResponse[Res](rawResponse)(using cmd.decoder) match
          case Right(response)   => Future.successful(response)
          case Left(decodeError) =>
            val tauriError = TauriError.InvokeError(
              cmd.id.value,
              s"Failed to decode response: $decodeError",
              None
            )
            Future.failed(tauriError)
      .recoverWith:
        case error: TauriError => Future.failed(error) // Already wrapped
        case error: Throwable  =>
          val tauriError = TauriError.InvokeError(
            cmd.id.value,
            s"Command invocation failed: ${error.getMessage}",
            Some(error)
          )
          Future.failed(tauriError)
  end invoke

  /** Invoke a Tauri command with parameters.
    *
    * This is the primary way to call parameterized Rust commands from the frontend. The command is
    * resolved from implicit scope using the Command typeclass.
    *
    * Errors are propagated through the Future's failure channel and wrapped in TauriError.
    *
    * @param req The request parameters
    * @param cmd The Command instance (resolved implicitly)
    * @param ec Execution context for async operations
    * @tparam Req The request parameter type
    * @tparam Res The expected return type
    * @return Future that succeeds with Res or fails with TauriError
    *
    * @example
    *   {{{
    * import tausi.api.core.*
    * import tausi.api.commands.window.{given, *}
    *
    * // Command[SetTitle, Unit] resolved implicitly
    * invoke(SetTitle("main", "My App"))
    *   }}}
    */
  def invoke[Req, Res](req: Req)(using
    cmd: Command[Req, Res],
    ec: ExecutionContext
  ): Future[Res] =
    val encodedRequest = Command.encodeRequest(req)(using cmd.encoder)

    val jsPromise = TauriInternalsGlobal.invoke[js.Any](
      cmd.id.value,
      encodedRequest,
      InvokeOptionsJS.empty
    )

    jsPromise.toFuture
      .flatMap: rawResponse =>
        Command.decodeResponse[Res](rawResponse)(using cmd.decoder) match
          case Right(response)   => Future.successful(response)
          case Left(decodeError) =>
            val tauriError = TauriError.InvokeError(
              cmd.id.value,
              s"Failed to decode response: $decodeError",
              None
            )
            Future.failed(tauriError)
      .recoverWith:
        case error: TauriError => Future.failed(error) // Already wrapped
        case error: Throwable  =>
          val tauriError = TauriError.InvokeError(
            cmd.id.value,
            s"Command invocation failed: ${error.getMessage}",
            Some(error)
          )
          Future.failed(tauriError)
  end invoke

  /** Convert a device file path to a URL that can be loaded by the webview.
    *
    * Uses the default "asset" protocol.
    *
    * @note
    *   The asset protocol must be properly configured in tauri.conf.json:
    *   - Add to CSP: "csp": "default-src 'self' ipc: http://ipc.localhost; img-src 'self' asset:
    *     http://asset.localhost"
    *   - Enable asset protocol: "assetProtocol": { "enable": true, "scope": [...] }
    *
    * @param filePath The file path to convert
    * @return Either a TauriError or the converted URL
    *
    * @example
    *   {{{
    * import tausi.api.core.*
    *
    * convertFileSrc("/path/to/image.png") match
    *   case Right(url) => println(s"Use URL: $url")
    *   case Left(error) => println(s"Error: ${error.message}")
    *   }}}
    */
  inline def convertFileSrc(filePath: String): Either[TauriError, String] =
    convertFileSrc(filePath, "asset")

  /** Convert a device file path to a URL that can be loaded by the webview.
    *
    * @param filePath The file path to convert
    * @param protocol The protocol to use
    * @return Either a TauriError or the converted URL
    */
  def convertFileSrc(
    filePath: String,
    protocol: String
  ): Either[TauriError, String] =
    Try {
      TauriInternalsGlobal.convertFileSrc(filePath, protocol)
    }.toEither.left.map: error =>
      TauriError.ConversionError.apply(
        filePath,
        protocol,
        s"Failed to convert file path: ${error.getMessage}",
        Some(error)
      )

  /** Check permissions for a plugin.
    *
    * This queries the current permission state for a plugin without requesting changes. Plugin
    * authors should wrap this in their plugin-specific permission checking logic.
    *
    * @note Internal utility - plugin-specific commands should define proper Command instances.
    *
    * @param plugin The plugin name
    * @param ec Execution context for async operations
    * @tparam T The permission response type (plugin-specific)
    * @return Future that succeeds with permission state or fails with TauriError
    */
  def checkPermissions[T](
    plugin: String
  )(using ec: ExecutionContext): Future[T] =
    val jsPromise = TauriInternalsGlobal.invoke[T](
      s"plugin:$plugin|check_permissions",
      js.Dictionary.empty[js.Any],
      InvokeOptionsJS.empty
    )

    jsPromise.toFuture.recoverWith:
      case error: Throwable =>
        val tauriError = TauriError.PermissionError(
          s"Failed to check permissions for plugin '$plugin': ${error.getMessage}",
          Some(error)
        )
        Future.failed(tauriError)
  end checkPermissions

  /** Request permissions for a plugin.
    *
    * This requests permission from the user if needed. The exact behaviour depends on the plugin
    * implementation and platform.
    *
    * @note Internal utility - plugin-specific commands should define proper Command instances.
    *
    * @param plugin The plugin name
    * @param ec Execution context for async operations
    * @tparam T The permission response type (plugin-specific)
    * @return Future that succeeds with permission state or fails with TauriError
    */
  def requestPermissions[T](
    plugin: String
  )(using ec: ExecutionContext): Future[T] =
    val jsPromise = TauriInternalsGlobal.invoke[T](
      s"plugin:$plugin|request_permissions",
      js.Dictionary.empty[js.Any],
      InvokeOptionsJS.empty
    )

    jsPromise.toFuture.recoverWith:
      case error: Throwable =>
        val tauriError = TauriError.PermissionError(
          s"Failed to request permissions for plugin '$plugin': ${error.getMessage}",
          Some(error)
        )
        Future.failed(tauriError)
  end requestPermissions

  /** Close a Tauri resource.
    *
    * Resources represent objects that live in the Rust process rather than in JavaScript. They must
    * be explicitly closed when done to free backend resources.
    *
    * @note Internal utility - will be replaced with proper Command instance in future.
    *
    * @param rid The resource identifier
    * @param ec Execution context for async operations
    * @return Future that succeeds with Unit or fails with TauriError
    *
    * @example
    *   {{{
    * import tausi.api.core.*
    *
    * // Close a resource directly
    * closeResource(resourceId).map(_ => println("Resource closed"))
    *
    * // Or use the extension method on Resource
    * resource.close()
    *   }}}
    */
  def closeResource(
    rid: ResourceId
  )(using ec: ExecutionContext): Future[Unit] =
    val jsPromise = TauriInternalsGlobal.invoke[Unit](
      "plugin:resources|close",
      js.Dictionary[js.Any]("rid" -> rid.toInt),
      InvokeOptionsJS.empty
    )

    jsPromise.toFuture.recoverWith:
      case error: Throwable =>
        val tauriError = TauriError.ResourceError(
          rid,
          s"Failed to close resource: ${error.getMessage}",
          Some(error)
        )
        Future.failed(tauriError)
  end closeResource

  extension [T](promise: js.Promise[T])
    private[tausi] inline def toFuture: Future[T] =
      val p = scala.concurrent.Promise[T]()
      promise.`then`[Unit](
        (value: T) => p.success(value): Unit,
        (error: Any) =>
          val throwable = inline error match
            case t: Throwable => t
            case _            => js.JavaScriptException(error)
          p.failure(throwable): Unit
      ): Unit
      p.future
  end extension
end core
