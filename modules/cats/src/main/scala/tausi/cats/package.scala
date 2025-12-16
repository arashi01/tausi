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
import tausi.api.codec.Decoder
import tausi.api.codec.Encoder
import tausi.api.{core as Core, event as CoreEvent, *}

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
    def listen[T: Decoder](name: String, handler: EventMessage[T] => Unit): IO[EventHandle] =
      listen(name, handler, EventOptions.default)

    def listen[T: Decoder](name: String, handler: EventMessage[T] => Unit, options: EventOptions): IO[EventHandle] =
      liftEventIO(CoreEvent.listen[T](name, handler, options))

    def listen[T: Decoder](name: TauriEvent, handler: EventMessage[T] => Unit): IO[EventHandle] =
      listen(name.value, handler)

    def listen[T: Decoder](name: TauriEvent, handler: EventMessage[T] => Unit, options: EventOptions): IO[EventHandle] =
      listen(name.value, handler, options)

    def once[T: Decoder](name: String, handler: EventMessage[T] => Unit): IO[EventHandle] =
      once(name, handler, EventOptions.default)

    def once[T: Decoder](name: String, handler: EventMessage[T] => Unit, options: EventOptions): IO[EventHandle] =
      liftEventIO(CoreEvent.once[T](name, handler, options))

    def once[T: Decoder](name: TauriEvent, handler: EventMessage[T] => Unit): IO[EventHandle] =
      once(name.value, handler)

    def once[T: Decoder](name: TauriEvent, handler: EventMessage[T] => Unit, options: EventOptions): IO[EventHandle] =
      once(name.value, handler, options)

    def emit(name: String): IO[Unit] =
      liftEventIO(CoreEvent.emit(name))

    def emit(name: TauriEvent): IO[Unit] = emit(name.value)

    def emit[T: Encoder](name: String, payload: T): IO[Unit] =
      liftEventIO(CoreEvent.emit[T](name, payload))

    def emit[T: Encoder](name: TauriEvent, payload: T): IO[Unit] =
      emit(name.value, payload)

    def emitTo(target: EventTarget, name: String): IO[Unit] =
      liftEventIO(CoreEvent.emitTo(target, name))

    def emitTo(target: EventTarget, name: TauriEvent): IO[Unit] =
      emitTo(target, name.value)

    def emitTo(label: String, name: String): IO[Unit] =
      liftEventIO(CoreEvent.emitTo(label, name))

    def emitTo[T: Encoder](target: EventTarget, name: String, payload: T): IO[Unit] =
      liftEventIO(CoreEvent.emitTo[T](target, name, payload))

    def emitTo[T: Encoder](target: EventTarget, name: TauriEvent, payload: T): IO[Unit] =
      emitTo(target, name.value, payload)

    def emitTo[T: Encoder](label: String, name: String, payload: T): IO[Unit] =
      liftEventIO(CoreEvent.emitTo[T](label, name, payload))

    def emitTo[T: Encoder](label: String, name: TauriEvent, payload: T): IO[Unit] =
      emitTo(label, name.value, payload)

    def unlisten(handle: EventHandle): IO[Unit] =
      liftEventIO(CoreEvent.unlisten(handle))

    def listenEval[T: Decoder](name: String)(handler: EventMessage[T] => IO[Unit])(using
      dispatcher: Dispatcher[IO]): Resource[IO, EventHandle] =
      listenEval(name, EventOptions.default)(handler)

    def listenEval[T: Decoder](name: String, options: EventOptions)(
      handler: EventMessage[T] => IO[Unit]
    )(using dispatcher: Dispatcher[IO]): Resource[IO, EventHandle] =
      listenWithCallback(name, options)(callbackFrom(handler))

    def listenEval[T: Decoder](name: TauriEvent)(
      handler: EventMessage[T] => IO[Unit]
    )(using dispatcher: Dispatcher[IO]): Resource[IO, EventHandle] =
      listenEval(name.value, EventOptions.default)(handler)

    def listenEval[T: Decoder](name: TauriEvent, options: EventOptions)(
      handler: EventMessage[T] => IO[Unit]
    )(using dispatcher: Dispatcher[IO]): Resource[IO, EventHandle] =
      listenEval(name.value, options)(handler)

    def stream[T: Decoder](name: String)(using Dispatcher[IO]): Stream[IO, EventMessage[T]] = stream(name, EventOptions.default)

    def stream[T: Decoder](name: String, options: EventOptions)(using Dispatcher[IO]): Stream[IO, EventMessage[T]] =
      Stream
        .resource {
          for
            queue <- Resource.eval(Queue.unbounded[IO, EventMessage[T]])
            _ <- listenEval(name, options)(event => queue.offer(event))
          yield queue
        }
        .flatMap(queue => Stream.fromQueueUnterminated(queue))

    def stream[T: Decoder](name: TauriEvent)(using
      Dispatcher[IO]
    ): Stream[IO, EventMessage[T]] = stream(name.value)

    def stream[T: Decoder](name: TauriEvent, options: EventOptions)(using
      Dispatcher[IO]
    ): Stream[IO, EventMessage[T]] = stream(name.value, options)

    private def listenWithCallback[T: Decoder](
      name: String,
      options: EventOptions
    )(callback: EventMessage[T] => Unit): Resource[IO, EventHandle] =
      managedHandle(listen(name, callback, options))

    private def managedHandle(io: IO[EventHandle]): Resource[IO, EventHandle] =
      Resource.make(io)(releaseHandle)

    private def releaseHandle(handle: EventHandle): IO[Unit] =
      unlisten(handle)

    private def callbackFrom[T](handler: EventMessage[T] => IO[Unit])(using dispatcher: Dispatcher[IO]): EventMessage[T] => Unit =
      event => dispatcher.unsafeRunAndForget(handler(event))
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
end cats
