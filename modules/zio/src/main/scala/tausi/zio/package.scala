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
package tausi

import scala.concurrent.ExecutionContext

import _root_.zio.IO
import _root_.zio.Queue
import _root_.zio.Runtime
import _root_.zio.Scope
import _root_.zio.Trace
import _root_.zio.UIO
import _root_.zio.Unsafe
import _root_.zio.ZIO
import _root_.zio.stream.ZStream

import tausi.api.Closeable
import tausi.api.Command
import tausi.api.Command0
import tausi.api.EventHandle
import tausi.api.EventMessage
import tausi.api.EventOptions
import tausi.api.EventTarget
import tausi.api.Resource
import tausi.api.ResourceId
import tausi.api.TauriError
import tausi.api.core as Core
import tausi.api.events as CoreEvent

/** ZIO integration for Tausi.
  *
  * Provides:
  *   - ZIO-based wrappers for all core Tauri operations
  *   - Typed error channel variants (IO[TauriError, A])
  *   - Extension methods for Either-based and error-channel variants
  *   - Scope-based lifecycle management
  *   - ZStream integration for channels
  */
package object zio:

  /** Check if running inside a Tauri application.
    *
    * @return ZIO containing true if running in Tauri, false otherwise
    */
  def isTauri: UIO[Boolean] = ZIO.succeed(Core.isTauri)

  // ====================
  // Command Invocation
  // ====================

  /** Invoke a zero-argument Tauri command.
    *
    * Errors from the Future failure channel are mapped to ZIO's error channel as TauriError.
    *
    * @param cmd The Command0 instance (resolved implicitly)
    * @tparam Res The expected return type
    * @return IO with TauriError in the error channel
    *
    * @example
    *   {{{
    * import tausi.zio.*
    * import tausi.api.commands.app.{given, *}
    *
    * val version: IO[TauriError, String] = invoke  // Command0[String] resolved implicitly
    *   }}}
    */
  def invoke[Res](using cmd: Command0[Res])(using Trace): IO[TauriError, Res] =
    ZIO.fromFuture(ec => Core.invoke[Res](using cmd, ec)).mapError(TauriError.fromThrowable)

  /** Invoke a Tauri command with parameters.
    *
    * Errors from the Future failure channel are mapped to ZIO's error channel as TauriError.
    *
    * @param req The request parameters
    * @param cmd The Command instance (resolved implicitly)
    * @tparam Req The request parameter type
    * @tparam Res The expected return type
    * @return IO with TauriError in the error channel
    *
    * @example
    *   {{{
    * import tausi.zio.*
    * import tausi.api.commands.window.{given, *}
    *
    * invoke(SetTitle("main", "My App"))
    *   }}}
    */
  def invoke[Req, Res](req: Req)(using cmd: Command[Req, Res])(using Trace): IO[TauriError, Res] =
    ZIO.fromFuture(ec => Core.invoke[Req, Res](req)(using cmd, ec)).mapError(TauriError.fromThrowable)

  // ====================
  // File Conversion
  // ====================

  /** Convert a device file path to a URL that can be loaded by the webview. Uses the default
    * "asset" protocol.
    *
    * @param filePath The file path to convert
    * @return UIO containing Either a TauriError or the converted URL
    */
  def convertFileSrc(filePath: String): UIO[Either[TauriError, String]] =
    ZIO.succeed(Core.convertFileSrc(filePath))

  /** Convert a device file path to a URL that can be loaded by the webview.
    *
    * @param filePath The file path to convert
    * @param protocol The protocol to use
    * @return UIO containing Either a TauriError or the converted URL
    */
  def convertFileSrc(filePath: String, protocol: String): UIO[Either[TauriError, String]] =
    ZIO.succeed(Core.convertFileSrc(filePath, protocol))

  // ====================
  // Permissions
  // ====================

  /** Check permissions for a plugin.
    *
    * @param plugin The plugin name
    * @tparam T The permission response type
    * @return IO with TauriError in the error channel
    */
  def checkPermissions[T](plugin: String)(using Trace): IO[TauriError, T] =
    ZIO.fromFuture(ec => Core.checkPermissions[T](plugin)(using ec)).mapError(TauriError.fromThrowable)

  /** Request permissions for a plugin.
    *
    * @param plugin The plugin name
    * @tparam T The permission response type
    * @return IO with TauriError in the error channel
    */
  def requestPermissions[T](plugin: String)(using Trace): IO[TauriError, T] =
    ZIO.fromFuture(ec => Core.requestPermissions[T](plugin)(using ec)).mapError(TauriError.fromThrowable)

  /** Close a Tauri resource.
    *
    * @param rid The resource identifier
    * @return IO with TauriError in the error channel
    */
  def closeResource(rid: ResourceId)(using Trace): IO[TauriError, Unit] =
    ZIO.fromFuture(ec => Core.closeResource(rid)(using ec)).mapError(TauriError.fromThrowable)

  // ====================
  // Event System
  // ====================

  object events:
    /** Register a persistent listener for an event with default options.
      *
      * The handler receives an Either containing either a decode error or the decoded event.
      * This follows the errors-as-values principle.
      *
      * @param handler
      *   Callback receiving Either decode error or decoded event message
      * @param ev
      *   The Event instance defining name and payload type
      * @return
      *   IO with TauriError in the error channel containing the event handle
      *
      * @example
      *   {{{
      * import tausi.zio.*
      * import myapp.events.AppEvents.{given, *}
      *
      * events.listen {
      *   case Right(msg) => println(msg.payload)
      *   case Left(err) => println(s"Decode error: ${err.message}")
      * }
      *   }}}
      */
    def listen[A](handler: Either[TauriError.EventError, EventMessage[A]] => Unit)(using
      ev: tausi.api.Event[A],
      trace: Trace
    ): IO[TauriError, EventHandle] =
      listen(handler, EventOptions.default)

    /** Register a persistent listener with explicit options. */
    def listen[A](handler: Either[TauriError.EventError, EventMessage[A]] => Unit, options: EventOptions)(using
      ev: tausi.api.Event[A],
      trace: Trace
    ): IO[TauriError, EventHandle] =
      liftEventIO(CoreEvent.listen(handler, options))

    /** Register a once-off listener with default options.
      *
      * The listener automatically unregisters after receiving the first event.
      */
    def once[A](handler: Either[TauriError.EventError, EventMessage[A]] => Unit)(using
      ev: tausi.api.Event[A],
      trace: Trace
    ): IO[TauriError, EventHandle] =
      once(handler, EventOptions.default)

    /** Register a once-off listener with explicit options. */
    def once[A](handler: Either[TauriError.EventError, EventMessage[A]] => Unit, options: EventOptions)(using
      ev: tausi.api.Event[A],
      trace: Trace
    ): IO[TauriError, EventHandle] =
      liftEventIO(CoreEvent.once(handler, options))

    /** Emit an event with a payload.
      *
      * The event name and encoder are resolved from the implicit [[tausi.api.Event]] instance.
      *
      * @param payload
      *   The payload to emit
      * @param ev
      *   The Event instance defining name and payload type
      * @return
      *   IO with TauriError in the error channel
      *
      * @example
      *   {{{
      * import tausi.zio.*
      * import myapp.events.AppEvents.{given, *}
      *
      * events.emit(SurveySubmission("test data"))
      *   }}}
      */
    def emit[A](payload: A)(using ev: tausi.api.Event[A], trace: Trace): IO[TauriError, Unit] =
      liftEventIO(CoreEvent.emit(payload))

    /** Emit an event to a specific target. */
    def emitTo[A](target: EventTarget, payload: A)(using
      ev: tausi.api.Event[A],
      trace: Trace
    ): IO[TauriError, Unit] =
      liftEventIO(CoreEvent.emitTo(target, payload))

    /** Emit an event to a specific label. */
    def emitTo[A](label: String, payload: A)(using
      ev: tausi.api.Event[A],
      trace: Trace
    ): IO[TauriError, Unit] =
      liftEventIO(CoreEvent.emitTo(label, payload))

    /** Unlisten using a previously obtained handle. */
    def unlisten(handle: EventHandle)(using Trace): IO[TauriError, Unit] =
      liftEventIO(CoreEvent.unlisten(handle))

    /** Listen to events with effect-based handler. Returns a scoped resource that automatically
      * unlistens when the scope closes.
      *
      * Decode errors from the event system are propagated through the handler's error channel.
      */
    def listenScoped[A](handler: EventMessage[A] => IO[TauriError, Unit])(using
      ev: tausi.api.Event[A],
      trace: Trace
    ): ZIO[Scope, TauriError, EventHandle] =
      listenScoped(handler, EventOptions.default)

    /** Listen with effect-based handler and explicit options.
      *
      * Decode errors from the event system are propagated through the handler's error channel.
      */
    def listenScoped[A](handler: EventMessage[A] => IO[TauriError, Unit], options: EventOptions)(using
      ev: tausi.api.Event[A],
      trace: Trace
    ): ZIO[Scope, TauriError, EventHandle] =
      ZIO.acquireRelease(
        listen(eitherCallbackFrom(handler), options)
      )(handle => unlisten(handle).orDie)

    /** Create a ZStream of events. The stream will emit events as they arrive and automatically
      * unlisten when the stream is closed.
      *
      * Decode errors are propagated through the stream's error channel.
      *
      * @example
      *   {{{
      * import tausi.zio.*
      * import myapp.events.AppEvents.{given, *}
      *
      * val stream: ZStream[Any, TauriError, EventMessage[SurveySubmission]] =
      *   events.stream[SurveySubmission]
      *   }}}
      */
    def stream[A](using ev: tausi.api.Event[A], trace: Trace): ZStream[Any, TauriError, EventMessage[A]] =
      stream(EventOptions.default)

    /** Create a ZStream with explicit options.
      *
      * Decode errors are propagated through the stream's error channel.
      */
    def stream[A](options: EventOptions)(using
      ev: tausi.api.Event[A],
      trace: Trace
    ): ZStream[Any, TauriError, EventMessage[A]] =
      org.scalajs.dom.console.log(s"[events.stream] Creating stream for event: ${ev.name}")
      ZStream.scoped {
        org.scalajs.dom.console.log("[events.stream] Scoped effect starting")
        for
          queue <- ZIO.acquireRelease(Queue.unbounded[Either[TauriError.EventError, EventMessage[A]]])(_.shutdown)
          _ = org.scalajs.dom.console.log("[events.stream] Queue created")
          eitherHandler: (Either[TauriError.EventError, EventMessage[A]] => Unit) =
            result =>
              org.scalajs.dom.console.log(s"[events.stream] Event handler invoked with: $result")
              _root_.zio.Unsafe.unsafe { implicit unsafe =>
                _root_.zio.Runtime.default.unsafe.run(queue.offer(result).unit).getOrThrowFiberFailure()
              }
          handle <- ZIO.acquireRelease(
                      ZIO.succeed(org.scalajs.dom.console.log("[events.stream] Registering event listener")) *>
                        listen(eitherHandler, options).tap(h =>
                          ZIO.succeed(org.scalajs.dom.console.log(s"[events.stream] Listener registered with handle: $h"))
                        )
                    )(handle => unlisten(handle).orDie)
        yield ZStream.fromQueue(queue).mapZIO(ZIO.fromEither(_).mapError(identity))
        end for
      }.flatten
    end stream

    /** Convert an effect-based handler to an Either-based callback.
      *
      * Errors in the Either (decode failures) are propagated through the ZIO error channel.
      */
    private def eitherCallbackFrom[A](
      handler: EventMessage[A] => IO[TauriError, Unit]
    ): Either[TauriError.EventError, EventMessage[A]] => Unit =
      result =>
        _root_.zio.Unsafe.unsafe { implicit unsafe =>
          val effect = result match
            case Right(event) => handler(event)
            case Left(err)    => ZIO.fail(err)
          _root_.zio.Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
        }
  end events

  private def liftEventIO[A](op: ExecutionContext ?=> scala.concurrent.Future[A])(using Trace): IO[TauriError, A] =
    ZIO.fromFuture(ec => op(using ec)).mapError(TauriError.fromThrowable)

  // ====================
  // Extensions for Resource Lifecycle
  // ====================

  extension (resource: Resource)
    /** Close this resource with ZIO encapsulation.
      *
      * @return IO with TauriError in the error channel
      */
    def closeZIO(using Trace): IO[TauriError, Unit] =
      ZIO.fromFuture(ec => resource.close()(using ec)).mapError(TauriError.fromThrowable)

  // ====================
  // Resource Lifecycle Management
  // ====================

  /** Extension methods for Closeable resources to integrate with ZIO Scope.
    *
    * Provides automatic resource management with ZIO's acquire-release pattern.
    *
    * Example:
    * {{{
    * import tausi.zio.*
    * import tausi.api.Closeable
    *
    * def useResource[R: Closeable](r: R): ZIO[Scope, TauriError, Unit] =
    *   r.toScoped.flatMap { resource =>
    *     // Use the resource safely
    *     // Cleanup happens automatically even on errors
    *     ZIO.debug("Using resource")
    *   }
    * }}}
    */
  extension [R](resource: R)(using closeable: Closeable[R])
    /** Convert a Closeable resource to a scoped ZIO effect.
      *
      * The resulting effect will automatically close the resource when the scope ends, even if an
      * error occurs during usage. This provides safe, composable resource management.
      *
      * @return ZIO effect with Scope requirement that manages the lifecycle
      *
      * @example
      *   {{{
      * import tausi.zio.*
      * import tausi.api.commands.image.{given, *}
      *
      * val program = ZIO.scoped {
      *   for {
      *     img <- invoke(FromPath("/path/to/image.png"))
      *     _ <- img.toScoped
      *     _ <- ZIO.debug(s"Image size: ${img.width}x${img.height}")
      *     // Image automatically closed when scope ends
      *   } yield ()
      * }
      *   }}}
      *
      * Complexity: O(1) + cleanup cost
      */
    def toScoped(using trace: Trace): ZIO[Scope, TauriError, R] =
      ZIO.acquireRelease(
        acquire = ZIO.succeed(resource)(using trace)
      )(release =
        r =>
          ZIO
            .fromFuture(ec => closeable.close(r)(using ec))(using trace)
            .mapError(TauriError.fromThrowable)
            .orDie // Convert to defect since release failures should not be recoverable
      )(using trace)

    /** Lift a Closeable resource into a scoped ZIO effect with acquisition.
      *
      * Similar to toScoped but allows specifying an acquisition action that may fail.
      *
      * @param acquire The ZIO action to acquire the resource
      * @return ZIO effect with Scope requirement that manages the lifecycle
      *
      * @example
      *   {{{
      * import tausi.zio.*
      *
      * def loadImage(path: String): ZIO[Scope, TauriError, MyImage] =
      *   MyImage.empty.asScoped {
      *     for {
      *       img <- invoke(FromPath(path))
      *     } yield img
      *   }
      *   }}}
      */
    def asScoped(acquire: ZIO[Any, TauriError, R])(using trace: Trace): ZIO[Scope, TauriError, R] =
      ZIO.acquireRelease(acquire)(release =
        r =>
          ZIO
            .fromFuture(ec => closeable.close(r)(using ec))(using trace)
            .mapError(TauriError.fromThrowable)
            .orDie
      )(using trace)
  end extension

  // ====================
  // Effect Execution Extensions
  // ====================

  /** Extension methods for executing ZIO effects from UI callbacks.
    *
    * These extensions provide ergonomic, type-safe effect execution suitable for integration with
    * UI frameworks like Laminar. All methods require an implicit `Runtime[Any]` in scope.
    *
    * @example
    *   {{{
    * import tausi.zio.*
    *
    * given Runtime[Any] = Runtime.default
    *
    * button(
    *   onClick --> { _ => submitCommand.runWith(handleSuccess, handleError) }
    * )
    *   }}}
    */
  extension [E <: Throwable, A](effect: IO[E, A])

    /** Execute the effect, invoking a callback on completion.
      *
      * The effect is forked to avoid blocking the UI thread. The callback receives the result as
      * an Either, with Left for errors and Right for success.
      *
      * @param onResult
      *   called with the result (success or failure as Either)
      */
    inline def runWith(onResult: Either[E, A] => Unit)(using runtime: Runtime[Any], trace: Trace): Unit =
      Unsafe.unsafe { implicit unsafe =>
        runtime.unsafe.fork(
          effect.foldZIO(
            e => ZIO.succeed(onResult(Left(e))),
            a => ZIO.succeed(onResult(Right(a)))
          )
        ): Unit
      }

    /** Execute the effect, invoking separate callbacks for success and failure.
      *
      * The effect is forked to avoid blocking the UI thread.
      *
      * @param onSuccess
      *   called if the effect succeeds
      * @param onError
      *   called if the effect fails
      */
    inline def runWith(onSuccess: A => Unit, onError: E => Unit)(using runtime: Runtime[Any], trace: Trace): Unit =
      Unsafe.unsafe { implicit unsafe =>
        runtime.unsafe.fork(
          effect.foldZIO(
            e => ZIO.succeed(onError(e)),
            a => ZIO.succeed(onSuccess(a))
          )
        ): Unit
      }

    /** Execute the effect, discarding both success and failure results.
      *
      * '''WARNING''': This is unsafe because errors are silently dropped. Use only when you
      * explicitly don't care about the result or errors (e.g., fire-and-forget logging).
      *
      * Prefer `runWith(onResult)` or `runWith(onSuccess, onError)` for proper error handling.
      */
    inline def runWithUnsafe()(using runtime: Runtime[Any], trace: Trace): Unit =
      Unsafe.unsafe { implicit unsafe =>
        runtime.unsafe.fork(effect.ignore): Unit
      }

  end extension
end zio
