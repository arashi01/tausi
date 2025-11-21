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

import _root_.cats.effect.IO
import _root_.cats.effect.kernel.Resource
import _root_.cats.effect.std.Dispatcher
import _root_.cats.effect.std.Queue

import _root_.fs2.Stream

import tausi.api.EventMessage
import tausi.api.{core as Core, event as CoreEvent, *}

/** Cats Effect integration for Tausi.
  *
  * Provides:
  *   - IO-based wrappers for all core Tauri operations
  *   - Extension methods for Either-based and error-channel variants
  *   - Resource-safe lifecycle management
  *   - fs2 Stream integration for channels
  */
package object cats:

  type Event[T] = EventMessage[T]

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
    * @param cmd The command name
    * @tparam T The expected return type
    * @return IO containing Either a TauriError or the result
    *
    * Complexity: O(1) + IPC latency
    */
  def invoke[T](cmd: String): IO[Either[TauriError, T]] =
    IO.executionContext.flatMap: ec =>
      IO.fromFuture(IO(Core.invoke[T](cmd)(using ec)))

  /** Invoke a Tauri command with arguments.
    *
    * @param cmd The command name
    * @param args The command arguments
    * @tparam T The expected return type
    * @return IO containing Either a TauriError or the result
    */
  def invoke[T](cmd: String, args: InvokeArgs): IO[Either[TauriError, T]] =
    IO.executionContext.flatMap: ec =>
      IO.fromFuture(IO(Core.invoke[T](cmd, args)(using ec)))

  /** Invoke a Tauri command with arguments and options.
    *
    * @param cmd The command name
    * @param args The command arguments
    * @param options Invoke options (e.g., custom headers)
    * @tparam T The expected return type
    * @return IO containing Either a TauriError or the result
    */
  def invoke[T](cmd: String, args: InvokeArgs, options: InvokeOptions): IO[Either[TauriError, T]] =
    IO.executionContext.flatMap: ec =>
      IO.fromFuture(IO(Core.invoke[T](cmd, args, options)(using ec)))

  // ====================
  // File Conversion
  // ====================

  /** Convert a device file path to a URL that can be loaded by the webview. Uses the default
    * "asset" protocol.
    *
    * @param filePath The file path to convert
    * @return IO containing Either a TauriError or the converted URL
    */
  def convertFileSrc(filePath: String): IO[Either[TauriError, String]] =
    IO.delay(Core.convertFileSrc(filePath))

  /** Convert a device file path to a URL that can be loaded by the webview.
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
  ): IO[Either[TauriError, PluginListener]] =
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
  def checkPermissions[T](plugin: String): IO[Either[TauriError, T]] =
    IO.executionContext.flatMap: ec =>
      IO.fromFuture(IO(Core.checkPermissions[T](plugin)(using ec)))

  /** Request permissions for a plugin.
    *
    * @param plugin The plugin name
    * @tparam T The permission response type
    * @return IO containing Either a TauriError or the permission state
    */
  def requestPermissions[T](plugin: String): IO[Either[TauriError, T]] =
    IO.executionContext.flatMap: ec =>
      IO.fromFuture(IO(Core.requestPermissions[T](plugin)(using ec)))

  /** Close a Tauri resource.
    *
    * @param rid The resource identifier
    * @return IO containing Either a TauriError or Unit
    */
  def closeResource(rid: ResourceId): IO[Either[TauriError, Unit]] =
    IO.executionContext.flatMap: ec =>
      IO.fromFuture(IO(Core.closeResource(rid)(using ec)))

  // ====================
  // Event System
  // ====================
  object events:
    def listen[T](name: String, handler: Event[T] => Unit): IO[Either[TauriError, EventHandle]] =
      listen(name, handler, EventOptions.default)

    def listen[T](name: String, handler: Event[T] => Unit, options: EventOptions): IO[Either[TauriError, EventHandle]] =
      liftEventIO(CoreEvent.listen[T](name, handler, options))

    def listen[T](name: TauriEvent, handler: Event[T] => Unit): IO[Either[TauriError, EventHandle]] =
      listen(name.value, handler)

    def listen[T](name: TauriEvent, handler: Event[T] => Unit, options: EventOptions): IO[Either[TauriError, EventHandle]] =
      listen(name.value, handler, options)

    def once[T](name: String, handler: Event[T] => Unit): IO[Either[TauriError, EventHandle]] =
      once(name, handler, EventOptions.default)

    def once[T](name: String, handler: Event[T] => Unit, options: EventOptions): IO[Either[TauriError, EventHandle]] =
      liftEventIO(CoreEvent.once[T](name, handler, options))

    def once[T](name: TauriEvent, handler: Event[T] => Unit): IO[Either[TauriError, EventHandle]] =
      once(name.value, handler)

    def once[T](name: TauriEvent, handler: Event[T] => Unit, options: EventOptions): IO[Either[TauriError, EventHandle]] =
      once(name.value, handler, options)

    def emit(name: String): IO[Either[TauriError, Unit]] =
      liftEventIO(CoreEvent.emit(name))

    def emit(name: TauriEvent): IO[Either[TauriError, Unit]] = emit(name.value)

    def emit[T](name: String, payload: T): IO[Either[TauriError, Unit]] =
      liftEventIO(CoreEvent.emit[T](name, payload))

    def emit[T](name: TauriEvent, payload: T): IO[Either[TauriError, Unit]] =
      emit(name.value, payload)

    def emitTo(target: EventTarget, name: String): IO[Either[TauriError, Unit]] =
      liftEventIO(CoreEvent.emitTo(target, name))

    def emitTo(target: EventTarget, name: TauriEvent): IO[Either[TauriError, Unit]] =
      emitTo(target, name.value)

    def emitTo(label: String, name: String): IO[Either[TauriError, Unit]] =
      liftEventIO(CoreEvent.emitTo(label, name))

    def emitTo(label: String, name: TauriEvent): IO[Either[TauriError, Unit]] =
      emitTo(label, name.value)

    def emitTo[T](target: EventTarget, name: String, payload: T): IO[Either[TauriError, Unit]] =
      liftEventIO(CoreEvent.emitTo[T](target, name, payload))

    def emitTo[T](target: EventTarget, name: TauriEvent, payload: T): IO[Either[TauriError, Unit]] =
      emitTo(target, name.value, payload)

    def emitTo[T](label: String, name: String, payload: T): IO[Either[TauriError, Unit]] =
      liftEventIO(CoreEvent.emitTo[T](label, name, payload))

    def emitTo[T](label: String, name: TauriEvent, payload: T): IO[Either[TauriError, Unit]] =
      emitTo(label, name.value, payload)

    def unlisten(handle: EventHandle): IO[Either[TauriError, Unit]] =
      liftEventIO(CoreEvent.unlisten(handle))

    def listenEval[T](name: String)(handler: Event[T] => IO[Unit])(using dispatcher: Dispatcher[IO]): Resource[IO, EventHandle] =
      listenEval(name, EventOptions.default)(handler)

    def listenEval[T](name: String, options: EventOptions)(
      handler: Event[T] => IO[Unit]
    )(using dispatcher: Dispatcher[IO]): Resource[IO, EventHandle] =
      listenWithCallback(name, options)(callbackFrom(handler))

    def listenEval[T](name: TauriEvent)(
      handler: Event[T] => IO[Unit]
    )(using dispatcher: Dispatcher[IO]): Resource[IO, EventHandle] =
      listenEval(name.value, EventOptions.default)(handler)

    def listenEval[T](name: TauriEvent, options: EventOptions)(
      handler: Event[T] => IO[Unit]
    )(using dispatcher: Dispatcher[IO]): Resource[IO, EventHandle] =
      listenEval(name.value, options)(handler)

    def stream[T](name: String)(using Dispatcher[IO]): Stream[IO, Event[T]] = stream(name, EventOptions.default)

    def stream[T](name: String, options: EventOptions)(using Dispatcher[IO]): Stream[IO, Event[T]] =
      Stream
        .resource {
          for
            queue <- Resource.eval(Queue.unbounded[IO, Event[T]])
            _ <- listenEval(name, options)(event => queue.offer(event))
          yield queue
        }
        .flatMap(queue => Stream.fromQueueUnterminated(queue))

    def stream[T](name: TauriEvent)(using
      Dispatcher[IO]
    ): Stream[IO, Event[T]] = stream(name.value)

    def stream[T](name: TauriEvent, options: EventOptions)(using
      Dispatcher[IO]
    ): Stream[IO, Event[T]] = stream(name.value, options)

    private def listenWithCallback[T](
      name: String,
      options: EventOptions
    )(callback: Event[T] => Unit): Resource[IO, EventHandle] =
      managedHandle(listen(name, callback, options))

    private def managedHandle(io: IO[Either[TauriError, EventHandle]]): Resource[IO, EventHandle] =
      Resource.make(acquireHandle(io))(releaseHandle)

    private def acquireHandle(io: IO[Either[TauriError, EventHandle]]): IO[EventHandle] =
      io.flatMap {
        case Right(handle) => IO.pure(handle)
        case Left(error)   => IO.raiseError(error)
      }

    private def releaseHandle(handle: EventHandle): IO[Unit] =
      unlisten(handle).flatMap:
        case Right(_)    => IO.unit
        case Left(error) => IO.raiseError(error)

    private def callbackFrom[T](handler: Event[T] => IO[Unit])(using dispatcher: Dispatcher[IO]): Event[T] => Unit =
      event => dispatcher.unsafeRunAndForget(handler(event))
  end events

  private def liftEventIO[A](op: ExecutionContext ?=> Future[Either[TauriError, A]]): IO[Either[TauriError, A]] =
    IO.executionContext.flatMap: ec =>
      IO.fromFuture(IO(op(using ec)))

  // ====================
  // Extensions for Either-based Results
  // ====================

  extension [A](io: IO[Either[TauriError, A]])
    /** Convert Either result to error channel. Raises TauriError in IO's error channel on Left. */
    def orRaise: IO[A] = io.flatMap:
      case Right(value) => IO.pure(value)
      case Left(error)  => IO.raiseError(error)

  extension (listener: PluginListener)
    /** Unregister this listener with IO encapsulation.
      *
      * @return IO containing Either a TauriError or Unit
      */
    def unregisterIO: IO[Either[TauriError, Unit]] =
      IO.executionContext.flatMap: ec =>
        IO.fromFuture(IO(listener.unregister()(using ec)))

  extension (resource: tausi.api.Resource)
    /** Close this resource with IO encapsulation.
      *
      * @return IO containing Either a TauriError or Unit
      */
    def closeIO: IO[Either[TauriError, Unit]] =
      IO.executionContext.flatMap: ec =>
        IO.fromFuture(IO(resource.close()(using ec)))
end cats
