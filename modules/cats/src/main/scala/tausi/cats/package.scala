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

  /** Invoke a Tauri command with no arguments.
    *
    * Errors are propagated through IO's error channel as TauriError.
    *
    * @param cmd The command name
    * @tparam T The expected return type
    * @return IO that succeeds with T or fails with TauriError
    *
    * Complexity: O(1) + IPC latency
    */
  def invoke[T](cmd: String): IO[T] =
    IO.executionContext.flatMap: ec =>
      IO.fromFuture(IO(Core.invoke[T](cmd)(using ec)))

  /** Invoke a Tauri command with arguments.
    *
    * Errors are propagated through IO's error channel as TauriError.
    *
    * @param cmd The command name
    * @param args The command arguments
    * @tparam T The expected return type
    * @return IO that succeeds with T or fails with TauriError
    */
  def invoke[T](cmd: String, args: InvokeArgs): IO[T] =
    IO.executionContext.flatMap: ec =>
      IO.fromFuture(IO(Core.invoke[T](cmd, args)(using ec)))

  /** Invoke a Tauri command with arguments and options.
    *
    * Errors are propagated through IO's error channel as TauriError.
    *
    * @param cmd The command name
    * @param args The command arguments
    * @param options Invoke options (e.g., custom headers)
    * @tparam T The expected return type
    * @return IO that succeeds with T or fails with TauriError
    */
  def invoke[T](cmd: String, args: InvokeArgs, options: InvokeOptions): IO[T] =
    IO.executionContext.flatMap: ec =>
      IO.fromFuture(IO(Core.invoke[T](cmd, args, options)(using ec)))

  // ====================
  // Typed Error Channel API (EitherT)
  // ====================

  /** Invoke a Tauri command with typed error channel (EitherT).
    *
    * Provides ZIO-like typed error channels using EitherT[IO, TauriError, T]. Use this when you
    * want explicit TauriError in the type signature for composable error handling.
    *
    * @param cmd The command name
    * @tparam T The expected return type
    * @return EitherT with TauriError in the error channel
    *
    * @example
    *   {{{
    * import tausi.cats.*
    *
    * val program: EitherT[IO, TauriError, String] = for {
    *   greeting <- invokeEither[String]("greet", js.Dictionary("name" -> "Alice"))
    *   result <- invokeEither[String]("process", js.Dictionary("data" -> greeting))
    * } yield result
    *
    * program.value.flatMap {
    *   case Right(result) => IO.println(s"Success: $result")
    *   case Left(error) => IO.println(s"TauriError: ${error.message}")
    * }
    *   }}}
    */
  def invokeEither[T](cmd: String): EitherT[IO, TauriError, T] =
    EitherT(invoke[T](cmd).attempt.map(_.left.map {
      case err: TauriError => err
      case err             => TauriError.fromThrowable(err)
    }))

  /** Invoke a Tauri command with arguments and typed error channel (EitherT). */
  def invokeEither[T](cmd: String, args: InvokeArgs): EitherT[IO, TauriError, T] =
    EitherT(invoke[T](cmd, args).attempt.map(_.left.map {
      case err: TauriError => err
      case err             => TauriError.fromThrowable(err)
    }))

  /** Invoke a Tauri command with arguments, options, and typed error channel (EitherT). */
  def invokeEither[T](cmd: String, args: InvokeArgs, options: InvokeOptions): EitherT[IO, TauriError, T] =
    EitherT(invoke[T](cmd, args, options).attempt.map(_.left.map {
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
  // Plugin Listeners
  // ====================

  /** Add a listener to a plugin event.
    *
    * @param plugin The plugin name
    * @param event The event name
    * @param callback Function to call when event is emitted
    * @tparam T The event payload type
    * @return IO containing Either a TauriError or a PluginListener
    */
  def addPluginListener[T](
    plugin: String,
    event: String,
    callback: T => Unit
  ): IO[PluginListener] =
    IO.executionContext.flatMap: ec =>
      IO.fromFuture(IO(Core.addPluginListener[T](plugin, event, callback)(using ec)))

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

  extension (listener: PluginListener)
    /** Unregister this listener with IO encapsulation.
      *
      * @return IO containing Either a TauriError or Unit
      */
    def unregisterIO: IO[Unit] =
      IO.executionContext.flatMap: ec =>
        IO.fromFuture(IO(listener.unregister()(using ec)))

  extension (resource: tausi.api.Resource)
    /** Close this resource with IO encapsulation.
      *
      * @return IO containing Either a TauriError or Unit
      */
    def closeIO: IO[Unit] =
      IO.executionContext.flatMap: ec =>
        IO.fromFuture(IO(resource.close()(using ec)))
end cats
