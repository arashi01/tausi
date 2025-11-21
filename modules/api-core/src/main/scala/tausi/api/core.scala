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
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import tausi.api.internal.*

/** Core Tauri API functions.
  *
  * This object contains the primary interface for interacting with the Tauri runtime from the
  * frontend. It mirrors the structure of Tauri's core.ts API.
  *
  * All functions return Either[TauriError, T] wrapped in Future for async operations. This provides
  * explicit error handling and composability.
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

  /** Invoke a Tauri command with no arguments.
    *
    * This is the primary way to call Rust commands from the frontend. Commands must be registered
    * in the Tauri application builder.
    *
    * @param cmd The command name
    * @param ec Execution context for async operations
    * @tparam T The expected return type
    * @return Future containing Either a TauriError or the result
    *
    * @example
    *   {{{
    * import tausi.api.core.*
    *
    * // Invoke a simple command with no args
    * invoke[String]("get_app_version")
    *   }}}
    */
  inline def invoke[T](
    cmd: String
  )(using ec: ExecutionContext): Future[Either[TauriError, T]] =
    invoke[T](cmd, js.Dictionary.empty, InvokeOptions.empty)

  /** Invoke a Tauri command with arguments.
    *
    * @param cmd The command name
    * @param args The command arguments
    * @param ec Execution context for async operations
    * @tparam T The expected return type
    * @return Future containing Either a TauriError or the result
    *
    * @example
    *   {{{
    * import tausi.api.core.*
    *
    * // Invoke with arguments
    * invoke[String]("greet", js.Dictionary("name" -> "World"))
    *   }}}
    */
  inline def invoke[T](cmd: String, args: InvokeArgs)(using ec: ExecutionContext): Future[Either[TauriError, T]] =
    invoke[T](cmd, args, InvokeOptions.empty)

  /** Invoke a Tauri command with arguments and options.
    *
    * @param cmd The command name
    * @param args The command arguments
    * @param options Invoke options (e.g., custom headers)
    * @param ec Execution context for async operations
    * @tparam T The expected return type
    * @return Future containing Either a TauriError or the result
    *
    * @example
    *   {{{
    * import tausi.api.core.*
    *
    * // Invoke with custom headers
    * val options = InvokeOptions(Map("Authorization" -> "Bearer token"))
    * invoke[User]("get_user", js.Dictionary("id" -> 42), options)
    *   }}}
    */
  def invoke[T](
    cmd: String,
    args: InvokeArgs,
    options: InvokeOptions
  )(using ec: ExecutionContext): Future[Either[TauriError, T]] =
    val jsPromise = TauriInternalsGlobal.invoke[T](
      cmd,
      args.asInstanceOf[js.Any], // scalafix:ok
      options.toJS
    )

    jsPromise.toFuture.transformWith:
      case Success(result) => Future.successful(Right(result))
      case Failure(error)  =>
        val tauriError = TauriError.InvokeError.apply(
          cmd,
          s"Command invocation failed: ${error.getMessage}",
          Some(error)
        )
        Future.successful(Left(tauriError))
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

  /** Add a listener to a plugin event.
    *
    * Plugin events allow plugins to notify the frontend of various occurrences. The callback will
    * be invoked each time the event is emitted.
    *
    * @param plugin The plugin name
    * @param event The event name
    * @param callback Function to call when event is emitted
    * @param ec Execution context for async operations
    * @tparam T The event payload type
    * @return Future containing Either a TauriError or a PluginListener
    *
    * @example
    *   {{{
    * import tausi.api.core.*
    *
    * addPluginListener[String]("my-plugin", "status-changed") { status =>
    *   println(s"Status changed to: $status")
    * }.foreach {
    *   case Right(listener) =>
    *     // Save listener to unregister later
    *     someCleanupCode.register(listener.unregister())
    *   case Left(error) =>
    *     println(s"Failed to register listener: ${error.message}")
    * }
    *   }}}
    */
  def addPluginListener[T](
    plugin: String,
    event: String,
    callback: T => Unit
  )(using ec: ExecutionContext): Future[Either[TauriError, PluginListener]] =
    val handler = Channel[T](callback)
    val args = js.Dictionary[Any]("event" -> event, "handler" -> handler.toJSAny)

    // Try the new snake_case API first
    TauriInternalsGlobal
      .invoke[Unit](
        s"plugin:$plugin|register_listener",
        args,
        InvokeOptionsJS.empty
      )
      .toFuture
      .transformWith:
        case Success(_) =>
          Future.successful(Right(PluginListener(plugin, event, handler.id)))
        case Failure(_) =>
          // Fall back to camelCase for backwards compatibility
          TauriInternalsGlobal
            .invoke[Unit](
              s"plugin:$plugin|registerListener",
              args,
              InvokeOptionsJS.empty
            )
            .toFuture
            .transformWith:
              case Success(_) =>
                Future.successful(Right(PluginListener(plugin, event, handler.id)))
              case Failure(error) =>
                val tauriError = TauriError.PluginError.apply(
                  plugin,
                  s"Failed to register listener for event '$event': ${error.getMessage}",
                  Some(error)
                )
                Future.successful(Left(tauriError))
  end addPluginListener

  /** Check permissions for a plugin.
    *
    * This queries the current permission state for a plugin without requesting changes. Plugin
    * authors should wrap this in their plugin-specific permission checking logic.
    *
    * @param plugin The plugin name
    * @param ec Execution context for async operations
    * @tparam T The permission response type (plugin-specific)
    * @return Future containing Either a TauriError or the permission state
    */
  def checkPermissions[T](
    plugin: String
  )(using ec: ExecutionContext): Future[Either[TauriError, T]] =
    invoke[T](s"plugin:$plugin|check_permissions").transformWith:
      case Success(result) => Future.successful(result)
      case Failure(error)  =>
        val tauriError = TauriError.PermissionError.apply(
          s"Failed to check permissions for plugin '$plugin': ${error.getMessage}",
          Some(error)
        )
        Future.successful(Left(tauriError))

  /** Request permissions for a plugin.
    *
    * This requests permission from the user if needed. The exact behaviour depends on the plugin
    * implementation and platform.
    *
    * @param plugin The plugin name
    * @param ec Execution context for async operations
    * @tparam T The permission response type (plugin-specific)
    * @return Future containing Either a TauriError or the permission state
    */
  def requestPermissions[T](
    plugin: String
  )(using ec: ExecutionContext): Future[Either[TauriError, T]] =
    invoke[T](s"plugin:$plugin|request_permissions").transformWith:
      case Success(result) => Future.successful(result)
      case Failure(error)  =>
        val tauriError = TauriError.PermissionError.apply(
          s"Failed to request permissions for plugin '$plugin': ${error.getMessage}",
          Some(error)
        )
        Future.successful(Left(tauriError))

  /** Close a Tauri resource.
    *
    * Resources represent objects that live in the Rust process rather than in JavaScript. They must
    * be explicitly closed when done to free backend resources.
    *
    * @param rid The resource identifier
    * @param ec Execution context for async operations
    * @return Future containing Either a TauriError or Unit
    *
    * @example
    *   {{{
    * import tausi.api.core.*
    *
    * // Close a resource directly
    * closeResource(resourceId)
    *
    * // Or use the extension method on Resource
    * resource.close()
    *   }}}
    */
  def closeResource(
    rid: ResourceId
  )(using ec: ExecutionContext): Future[Either[TauriError, Unit]] =
    invoke[Unit](
      "plugin:resources|close",
      js.Dictionary("rid" -> rid.toInt)
    ).transformWith:
      case Success(result) => Future.successful(result)
      case Failure(error)  =>
        val tauriError = TauriError.ResourceError.apply(
          rid,
          s"Failed to close resource: ${error.getMessage}",
          Some(error)
        )
        Future.successful(Left(tauriError))

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
