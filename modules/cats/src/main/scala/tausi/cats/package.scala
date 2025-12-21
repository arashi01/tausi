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
import scala.concurrent.Future

import _root_.cats.data.EitherT
import _root_.cats.effect.IO
import _root_.cats.effect.kernel.Resource
import _root_.cats.effect.std.Dispatcher
import _root_.cats.effect.std.Queue

import _root_.fs2.Stream

import tausi.api.EventMessage
import tausi.api.{core as Core, events as CoreEvent, *}

/** Cats Effect integration for Tausi.
  *
  * Single import provides two complementary error handling styles:
  *
  * **Primary API (IO error channel):**
  * {{{
  * import tausi.cats.*
  *
  * val program: IO[String] = invoke("greet", js.Dictionary("name" -> "Alice"))
  * program.handleErrorWith {
  *   case err: TauriError => IO.println(s"Error: ${err.message}")
  *   case err => IO.raiseError(err)
  * }
  * }}}
  *
  * **Typed Error Channel API (EitherT) - for ZIO-like explicit error types:**
  * {{{
  * import tausi.cats.*
  *
  * val program: EitherT[IO, TauriError, String] =
  *   invokeEither("greet", js.Dictionary("name" -> "Alice"))
  *
  * program.value.flatMap {
  *   case Right(result) => IO.println(result)
  *   case Left(error: TauriError) => IO.println(s"TauriError: ${error.message}")
  * }
  * }}}
  *
  * Additional features:
  *   - Resource-safe lifecycle management with `events.listenEval`
  *   - fs2 Stream integration for event channels with `events.stream`
  *   - Extension methods: `listener.unregisterIO`, `resource.closeIO`
  */
package object cats:

  /** Check if running inside a Tauri application.
    *
    * @return IO containing true if running in Tauri, false otherwise
    */
  def isTauri: IO[Boolean] = IO.pure(Core.isTauri)

  // ====================
  // Command Invocation
  // ====================

  /** Invoke a zero-argument Tauri command.
    *
    * Errors are propagated through IO's error channel as TauriError.
    *
    * @param cmd The Command0 instance (resolved implicitly)
    * @tparam Res The expected return type
    * @return IO that succeeds with Res or fails with TauriError
    *
    * @example {{{import tausi.cats.* import tausi.api.commands.app.{given, *}
    *
    * val version: IO[String] = invoke // Command0[String] resolved implicitly }}}
    *
    * Complexity: O(1) + IPC latency
    */
  def invoke[Res](using cmd: Command0[Res]): IO[Res] =
    IO.executionContext.flatMap: ec =>
      IO.fromFuture(IO(Core.invoke[Res](using cmd, ec)))

  /** Invoke a Tauri command with parameters.
    *
    * Errors are propagated through IO's error channel as TauriError.
    *
    * @param req The request parameters
    * @param cmd The Command instance (resolved implicitly)
    * @tparam Req The request parameter type
    * @tparam Res The expected return type
    * @return IO that succeeds with Res or fails with TauriError
    *
    * @example {{{import tausi.cats.* import tausi.api.commands.window.{given, *}
    *
    * invoke(SetTitle("main", "My App")) }}}
    */
  def invoke[Req, Res](req: Req)(using cmd: Command[Req, Res]): IO[Res] =
    IO.executionContext.flatMap: ec =>
      IO.fromFuture(IO(Core.invoke[Req, Res](req)(using cmd, ec)))

  // ====================
  // Typed Error Channel API (EitherT)
  // ====================

  /** Invoke a zero-argument command with typed error channel (EitherT).
    *
    * Provides ZIO-like typed error channels using EitherT[IO, TauriError, T]. Use this when you
    * want explicit TauriError in the type signature for composable error handling.
    *
    * @param cmd The Command0 instance (resolved implicitly)
    * @tparam Res The expected return type
    * @return EitherT with TauriError in the error channel
    *
    * @example
    *   {{{
    * import tausi.cats.*
    * import tausi.api.commands.app.{given, *}
    *
    * val program: EitherT[IO, TauriError, String] = for {
    *   version <- invokeEither[String]
    *   name <- invokeEither[String](using app.name)
    * } yield s"$name v$version"
    *
    * program.value.flatMap {
    *   case Right(result) => IO.println(s"Success: $result")
    *   case Left(error) => IO.println(s"TauriError: ${error.message}")
    * }
    *   }}}
    */
  def invokeEither[Res](using cmd: Command0[Res]): EitherT[IO, TauriError, Res] =
    EitherT(invoke[Res].attempt.map(_.left.map {
      case err: TauriError => err
      case err             => TauriError.fromThrowable(err)
    }))

  /** Invoke a command with parameters and typed error channel (EitherT). */
  def invokeEither[Req, Res](req: Req)(using cmd: Command[Req, Res]): EitherT[IO, TauriError, Res] =
    EitherT(invoke[Req, Res](req).attempt.map(_.left.map {
      case err: TauriError => err
      case err             => TauriError.fromThrowable(err)
    }))

  // ====================
  // File Conversion
  // ====================

  /** Convert a device file path to a URL that can be loaded by the webview. Uses the default
    * "asset" protocol.
    *
    * Note: This is a synchronous operation that returns Either in the success channel, unlike async
    * invoke operations which use the error channel.
    *
    * @param filePath The file path to convert
    * @return IO containing Either a TauriError or the converted URL
    */
  def convertFileSrc(filePath: String): IO[Either[TauriError, String]] =
    IO.delay(Core.convertFileSrc(filePath))

  /** Convert a device file path to a URL that can be loaded by the webview.
    *
    * Note: This is a synchronous operation that returns Either in the success channel, unlike async
    * invoke operations which use the error channel.
    *
    * @param filePath The file path to convert
    * @param protocol The protocol to use
    * @return IO containing Either a TauriError or the converted URL
    */
  def convertFileSrc(filePath: String, protocol: String): IO[Either[TauriError, String]] =
    IO.delay(Core.convertFileSrc(filePath, protocol))

  // ====================
  // Permissions
  // ====================

  /** Check permissions for a plugin.
    *
    * @param plugin The plugin name
    * @tparam T The permission response type
    * @return IO containing Either a TauriError or the permission state
    */
  def checkPermissions[T](plugin: String): IO[T] =
    IO.executionContext.flatMap: ec =>
      IO.fromFuture(IO(Core.checkPermissions[T](plugin)(using ec)))

  /** Request permissions for a plugin.
    *
    * @param plugin The plugin name
    * @tparam T The permission response type
    * @return IO containing Either a TauriError or the permission state
    */
  def requestPermissions[T](plugin: String): IO[T] =
    IO.executionContext.flatMap: ec =>
      IO.fromFuture(IO(Core.requestPermissions[T](plugin)(using ec)))

  /** Close a Tauri resource.
    *
    * @param rid The resource identifier
    * @return IO containing Either a TauriError or Unit
    */
  def closeResource(rid: ResourceId): IO[Unit] =
    IO.executionContext.flatMap: ec =>
      IO.fromFuture(IO(Core.closeResource(rid)(using ec)))

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
      *   IO containing the event handle for unlistening
      *
      * @example
      *   {{{
      * import tausi.cats.*
      * import myapp.events.AppEvents.{given, *}
      *
      * events.listen {
      *   case Right(msg) => println(msg.payload)
      *   case Left(err) => println(s"Decode error: ${err.message}")
      * }
      *   }}}
      */
    def listen[A](handler: Either[TauriError.EventError, EventMessage[A]] => Unit)(using
      ev: tausi.api.Event[A]
    ): IO[EventHandle] =
      listen(handler, EventOptions.default)

    /** Register a persistent listener with explicit options. */
    def listen[A](handler: Either[TauriError.EventError, EventMessage[A]] => Unit, options: EventOptions)(using
      ev: tausi.api.Event[A]
    ): IO[EventHandle] =
      liftEventIO(CoreEvent.listen(handler, options))

    /** Register a once-off listener with default options.
      *
      * The listener automatically unregisters after receiving the first event.
      */
    def once[A](handler: Either[TauriError.EventError, EventMessage[A]] => Unit)(using
      ev: tausi.api.Event[A]
    ): IO[EventHandle] =
      once(handler, EventOptions.default)

    /** Register a once-off listener with explicit options. */
    def once[A](handler: Either[TauriError.EventError, EventMessage[A]] => Unit, options: EventOptions)(using
      ev: tausi.api.Event[A]
    ): IO[EventHandle] =
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
      *   IO completing when the event is emitted
      *
      * @example
      *   {{{
      * import tausi.cats.*
      * import myapp.events.AppEvents.{given, *}
      *
      * events.emit(SurveySubmission("test data"))
      *   }}}
      */
    def emit[A](payload: A)(using ev: tausi.api.Event[A]): IO[Unit] =
      liftEventIO(CoreEvent.emit(payload))

    /** Emit an event to a specific target. */
    def emitTo[A](target: EventTarget, payload: A)(using ev: tausi.api.Event[A]): IO[Unit] =
      liftEventIO(CoreEvent.emitTo(target, payload))

    /** Emit an event to a specific label. */
    def emitTo[A](label: String, payload: A)(using ev: tausi.api.Event[A]): IO[Unit] =
      liftEventIO(CoreEvent.emitTo(label, payload))

    /** Unlisten using a previously obtained handle. */
    def unlisten(handle: EventHandle): IO[Unit] =
      liftEventIO(CoreEvent.unlisten(handle))

    /** Listen to events with effect-based handler. Returns a Resource that automatically unlistens
      * when released.
      *
      * Decode errors from the event system are raised through the IO error channel.
      */
    def listenEval[A](handler: EventMessage[A] => IO[Unit])(using
      ev: tausi.api.Event[A],
      dispatcher: Dispatcher[IO]
    ): Resource[IO, EventHandle] =
      listenEval(handler, EventOptions.default)

    /** Listen with effect-based handler and explicit options.
      *
      * Decode errors from the event system are raised through the IO error channel.
      */
    def listenEval[A](handler: EventMessage[A] => IO[Unit], options: EventOptions)(using
      ev: tausi.api.Event[A],
      dispatcher: Dispatcher[IO]
    ): Resource[IO, EventHandle] =
      managedHandle(listen(eitherCallbackFrom(handler), options))

    /** Create an fs2 Stream of events. The stream will emit events as they arrive and automatically
      * unlisten when the stream is closed.
      *
      * Decode errors are raised through the stream's error channel.
      *
      * @example
      *   {{{
      * import tausi.cats.*
      * import myapp.events.AppEvents.{given, *}
      *
      * val stream: Stream[IO, EventMessage[SurveySubmission]] =
      *   events.stream[SurveySubmission]
      *   }}}
      */
    def stream[A](using ev: tausi.api.Event[A], dispatcher: Dispatcher[IO]): Stream[IO, EventMessage[A]] =
      stream(EventOptions.default)

    /** Create an fs2 Stream with explicit options.
      *
      * Decode errors are raised through the stream's error channel.
      */
    def stream[A](options: EventOptions)(using
      ev: tausi.api.Event[A],
      dispatcher: Dispatcher[IO]
    ): Stream[IO, EventMessage[A]] =
      Stream
        .resource {
          for
            queue <- Resource.eval(Queue.unbounded[IO, Either[TauriError.EventError, EventMessage[A]]])
            eitherHandler: (Either[TauriError.EventError, EventMessage[A]] => Unit) =
              result => dispatcher.unsafeRunAndForget(queue.offer(result))
            _ <- managedHandle(listen(eitherHandler, options))
          yield queue
        }
        .flatMap(queue => Stream.fromQueueUnterminated(queue))
        .evalMap(IO.fromEither)

    private def managedHandle(io: IO[EventHandle]): Resource[IO, EventHandle] =
      Resource.make(io)(releaseHandle)

    private def releaseHandle(handle: EventHandle): IO[Unit] =
      unlisten(handle)

    /** Convert an effect-based handler to an Either-based callback.
      *
      * Errors in the Either (decode failures) are raised through the IO error channel.
      */
    private def eitherCallbackFrom[A](handler: EventMessage[A] => IO[Unit])(using
      dispatcher: Dispatcher[IO]
    ): Either[TauriError.EventError, EventMessage[A]] => Unit =
      result =>
        val effect = result match
          case Right(event) => handler(event)
          case Left(err)    => IO.raiseError(err)
        dispatcher.unsafeRunAndForget(effect)
  end events

  private def liftEventIO[A](op: ExecutionContext ?=> Future[A]): IO[A] =
    IO.executionContext.flatMap: ec =>
      IO.fromFuture(IO(op(using ec)))

  extension (resource: tausi.api.Resource)
    /** Close this resource with IO encapsulation.
      *
      * @return IO containing Either a TauriError or Unit
      */
    def closeIO: IO[Unit] =
      IO.executionContext.flatMap: ec =>
        IO.fromFuture(IO(resource.close()(using ec)))

  // ====================
  // Resource Lifecycle Management
  // ====================

  /** Extension methods for Closeable resources to integrate with cats-effect Resource.
    *
    * Provides automatic resource management with bracket pattern and composable cleanup.
    *
    * Example:
    * {{{
    * import tausi.cats.*
    * import tausi.api.Closeable
    *
    * def useResource[R: Closeable](r: R): IO[Unit] =
    *   r.toResource.use { resource =>
    *     // Use the resource safely
    *     // Cleanup happens automatically even on errors
    *     IO.println("Using resource")
    *   }
    * }}}
    */
  extension [R](resource: R)(using closeable: Closeable[R])
    /** Convert a Closeable resource to a cats-effect Resource.
      *
      * The resulting Resource will automatically close the resource when it's released, even if an
      * error occurs during usage. This provides safe, composable resource management.
      *
      * @return cats-effect Resource that manages the lifecycle
      *
      * @example
      *   {{{
      * import tausi.cats.*
      * import tausi.api.commands.image.{given, *}
      *
      * val program = for {
      *   img <- invoke(FromPath("/path/to/image.png"))
      *   _ <- img.toResource.use { image =>
      *     // Use the image safely
      *     IO.println(s"Image size: ${image.width}x${image.height}")
      *   }
      *   // Image automatically closed here
      * } yield ()
      *   }}}
      *
      * Complexity: O(1) + cleanup cost
      */
    def toResource: Resource[IO, R] =
      Resource.make(IO.pure(resource)): r =>
        IO.executionContext.flatMap: ec =>
          IO.fromFuture(IO(closeable.close(r)(using ec)))

    /** Lift a Closeable resource into a cats-effect Resource with acquisition.
      *
      * Similar to toResource but allows specifying an acquisition action.
      *
      * @param acquire The IO action to acquire the resource
      * @return cats-effect Resource that manages the lifecycle
      */
    def asResource(acquire: IO[R]): Resource[IO, R] =
      Resource.make(acquire): r =>
        IO.executionContext.flatMap: ec =>
          IO.fromFuture(IO(closeable.close(r)(using ec)))
  end extension

  // ====================
  // Effect Execution Extensions
  // ====================

  /** Extension methods for executing Cats Effect IO from UI callbacks.
    *
    * These extensions provide ergonomic, type-safe effect execution suitable for integration with
    * UI frameworks like Laminar. All methods require an implicit `Dispatcher[IO]` in scope.
    *
    * @example
    *   {{{
    * import tausi.cats.*
    *
    * given Dispatcher[IO] = ??? // from IOApp or Resource
    *
    * button(
    *   onClick --> { _ => submitCommand.runWith(handleSuccess, handleError) }
    * )
    *   }}}
    */
  extension [A](effect: IO[A])

    /** Execute the effect, invoking a callback on completion.
      *
      * The effect is run via the Dispatcher with fire-and-forget semantics. The callback receives
      * the result as an Either, with Left for errors and Right for success.
      *
      * @param onResult
      *   called with the result (success or failure as Either)
      */
    inline def runWith(onResult: Either[Throwable, A] => Unit)(using dispatcher: Dispatcher[IO]): Unit =
      dispatcher.unsafeRunAndForget(
        effect.attempt.flatMap(result => IO(onResult(result)))
      )

    /** Execute the effect, invoking separate callbacks for success and failure.
      *
      * The effect is run via the Dispatcher with fire-and-forget semantics.
      *
      * @param onSuccess
      *   called if the effect succeeds
      * @param onError
      *   called if the effect fails
      */
    inline def runWith(onSuccess: A => Unit, onError: Throwable => Unit)(using dispatcher: Dispatcher[IO]): Unit =
      dispatcher.unsafeRunAndForget(
        effect.attempt.flatMap {
          case Right(a) => IO(onSuccess(a))
          case Left(e)  => IO(onError(e))
        }
      )

    /** Execute the effect, discarding both success and failure results.
      *
      * '''WARNING''': This is unsafe because errors are silently dropped. Use only when you
      * explicitly don't care about the result or errors (e.g., fire-and-forget logging).
      *
      * Prefer `runWith(onResult)` or `runWith(onSuccess, onError)` for proper error handling.
      */
    inline def runWithUnsafe()(using dispatcher: Dispatcher[IO]): Unit =
      dispatcher.unsafeRunAndForget(effect.void.handleError(_ => ()))

  end extension
end cats
