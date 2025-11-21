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
import _root_.zio.Scope
import _root_.zio.Trace
import _root_.zio.UIO
import _root_.zio.ZIO
import _root_.zio.stream.ZStream

import tausi.api.EventHandle
import tausi.api.EventMessage
import tausi.api.EventOptions
import tausi.api.EventTarget
import tausi.api.InvokeArgs
import tausi.api.InvokeOptions
import tausi.api.PluginListener
import tausi.api.Resource
import tausi.api.ResourceId
import tausi.api.TauriError
import tausi.api.TauriEvent
import tausi.api.core as Core
import tausi.api.event as CoreEvent

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

  /** Invoke a Tauri command with no arguments.
    *
    * @param cmd The command name
    * @tparam T The expected return type
    * @return IO with TauriError in the error channel
    */
  def invoke[T](cmd: String)(using Trace): IO[TauriError, T] =
    ZIO.fromFuture(ec => Core.invoke[T](cmd)(using ec)).absolve.mapError(TauriError.fromThrowable)

  /** Invoke a Tauri command with arguments.
    *
    * @param cmd The command name
    * @param args The command arguments
    * @tparam T The expected return type
    * @return IO with TauriError in the error channel
    */
  def invoke[T](cmd: String, args: InvokeArgs)(using Trace): IO[TauriError, T] =
    ZIO.fromFuture(ec => Core.invoke[T](cmd, args)(using ec)).absolve.mapError(TauriError.fromThrowable)

  /** Invoke a Tauri command with arguments and options.
    *
    * @param cmd The command name
    * @param args The command arguments
    * @param options Invoke options (e.g., custom headers)
    * @tparam T The expected return type
    * @return IO with TauriError in the error channel
    */
  def invoke[T](cmd: String, args: InvokeArgs, options: InvokeOptions)(using Trace): IO[TauriError, T] =
    ZIO.fromFuture(ec => Core.invoke[T](cmd, args, options)(using ec)).absolve.mapError(TauriError.fromThrowable)

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
  // Plugin Listeners
  // ====================

  /** Add a listener to a plugin event.
    *
    * @param plugin The plugin name
    * @param event The event name
    * @param callback Function to call when event is emitted
    * @tparam T The event payload type
    * @return IO with TauriError in the error channel
    */
  def addPluginListener[T](
    plugin: String,
    event: String,
    callback: T => Unit
  )(using Trace): IO[TauriError, PluginListener] =
    ZIO.fromFuture(ec => Core.addPluginListener[T](plugin, event, callback)(using ec)).absolve.mapError(TauriError.fromThrowable)

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
    ZIO.fromFuture(ec => Core.checkPermissions[T](plugin)(using ec)).absolve.mapError(TauriError.fromThrowable)

  /** Request permissions for a plugin.
    *
    * @param plugin The plugin name
    * @tparam T The permission response type
    * @return IO with TauriError in the error channel
    */
  def requestPermissions[T](plugin: String)(using Trace): IO[TauriError, T] =
    ZIO.fromFuture(ec => Core.requestPermissions[T](plugin)(using ec)).absolve.mapError(TauriError.fromThrowable)

  /** Close a Tauri resource.
    *
    * @param rid The resource identifier
    * @return IO with TauriError in the error channel
    */
  def closeResource(rid: ResourceId)(using Trace): IO[TauriError, Unit] =
    ZIO.fromFuture(ec => Core.closeResource(rid)(using ec)).absolve.mapError(TauriError.fromThrowable)

  // ====================
  // Event System
  // ====================

  type Event[T] = EventMessage[T]

  object events:
    def listen[T](name: String, handler: Event[T] => Unit)(using Trace): IO[TauriError, EventHandle] =
      listen(name, handler, EventOptions.default)

    def listen[T](name: String, handler: Event[T] => Unit, options: EventOptions)(using Trace): IO[TauriError, EventHandle] =
      liftEventIO(CoreEvent.listen[T](name, handler, options))

    def listen[T](name: TauriEvent, handler: Event[T] => Unit)(using Trace): IO[TauriError, EventHandle] =
      listen(name.value, handler)

    def listen[T](name: TauriEvent, handler: Event[T] => Unit, options: EventOptions)(using Trace): IO[TauriError, EventHandle] =
      listen(name.value, handler, options)

    def once[T](name: String, handler: Event[T] => Unit)(using Trace): IO[TauriError, EventHandle] =
      once(name, handler, EventOptions.default)

    def once[T](name: String, handler: Event[T] => Unit, options: EventOptions)(using Trace): IO[TauriError, EventHandle] =
      liftEventIO(CoreEvent.once[T](name, handler, options))

    def once[T](name: TauriEvent, handler: Event[T] => Unit)(using Trace): IO[TauriError, EventHandle] =
      once(name.value, handler)

    def once[T](name: TauriEvent, handler: Event[T] => Unit, options: EventOptions)(using Trace): IO[TauriError, EventHandle] =
      once(name.value, handler, options)

    def emit(name: String)(using Trace): IO[TauriError, Unit] =
      liftEventIO(CoreEvent.emit(name))

    def emit(name: TauriEvent)(using Trace): IO[TauriError, Unit] =
      emit(name.value)

    def emit[T](name: String, payload: T)(using Trace): IO[TauriError, Unit] =
      liftEventIO(CoreEvent.emit[T](name, payload))

    def emit[T](name: TauriEvent, payload: T)(using Trace): IO[TauriError, Unit] =
      emit(name.value, payload)

    def emitTo(target: EventTarget, name: String)(using Trace): IO[TauriError, Unit] =
      liftEventIO(CoreEvent.emitTo(target, name))

    def emitTo(target: EventTarget, name: TauriEvent)(using Trace): IO[TauriError, Unit] =
      emitTo(target, name.value)

    def emitTo(label: String, name: String)(using Trace): IO[TauriError, Unit] =
      liftEventIO(CoreEvent.emitTo(label, name))

    def emitTo(label: String, name: TauriEvent)(using Trace): IO[TauriError, Unit] =
      emitTo(label, name.value)

    def emitTo[T](target: EventTarget, name: String, payload: T)(using Trace): IO[TauriError, Unit] =
      liftEventIO(CoreEvent.emitTo[T](target, name, payload))

    def emitTo[T](target: EventTarget, name: TauriEvent, payload: T)(using Trace): IO[TauriError, Unit] =
      emitTo(target, name.value, payload)

    def emitTo[T](label: String, name: String, payload: T)(using Trace): IO[TauriError, Unit] =
      liftEventIO(CoreEvent.emitTo[T](label, name, payload))

    def emitTo[T](label: String, name: TauriEvent, payload: T)(using Trace): IO[TauriError, Unit] =
      emitTo(label, name.value, payload)

    def unlisten(handle: EventHandle)(using Trace): IO[TauriError, Unit] =
      liftEventIO(CoreEvent.unlisten(handle))

    /** Listen to events with effect-based handler. Returns a scoped resource that automatically
      * unlistens when the scope closes.
      */
    def listenScoped[T](name: String)(
      handler: Event[T] => IO[TauriError, Unit]
    )(using Trace): ZIO[Scope, TauriError, EventHandle] =
      listenScoped(name, EventOptions.default)(handler)

    def listenScoped[T](name: String, options: EventOptions)(
      handler: Event[T] => IO[TauriError, Unit]
    )(using Trace): ZIO[Scope, TauriError, EventHandle] =
      ZIO.acquireRelease(
        listenWithCallback(name, options)(unsafeCallbackFrom(handler))
      )(handle => unlisten(handle).ignore)

    def listenScoped[T](name: TauriEvent)(
      handler: Event[T] => IO[TauriError, Unit]
    )(using Trace): ZIO[Scope, TauriError, EventHandle] =
      listenScoped(name.value, EventOptions.default)(handler)

    def listenScoped[T](name: TauriEvent, options: EventOptions)(
      handler: Event[T] => IO[TauriError, Unit]
    )(using Trace): ZIO[Scope, TauriError, EventHandle] =
      listenScoped(name.value, options)(handler)

    /** Create a ZStream of events. The stream will emit events as they arrive and automatically
      * unlisten when the stream is closed.
      */
    def stream[T](name: String)(using Trace): ZStream[Any, TauriError, Event[T]] =
      stream(name, EventOptions.default)

    def stream[T](name: String, options: EventOptions)(using Trace): ZStream[Any, TauriError, Event[T]] =
      ZStream.scoped {
        for
          queue <- ZIO.acquireRelease(Queue.unbounded[Event[T]])(_.shutdown)
          _ <- listenScoped[T](name, options)((event: Event[T]) => queue.offer(event).unit)
        yield ZStream.fromQueue(queue)
      }.flatten

    def stream[T](name: TauriEvent)(using Trace): ZStream[Any, TauriError, Event[T]] =
      stream(name.value)

    def stream[T](name: TauriEvent, options: EventOptions)(using Trace): ZStream[Any, TauriError, Event[T]] =
      stream(name.value, options)

    private def listenWithCallback[T](
      name: String,
      options: EventOptions
    )(callback: Event[T] => Unit)(using Trace): IO[TauriError, EventHandle] =
      listen(name, callback, options)

    private def unsafeCallbackFrom[T](handler: Event[T] => IO[TauriError, Unit]): Event[T] => Unit =
      event =>
        _root_.zio.Unsafe.unsafe { implicit unsafe =>
          _root_.zio.Runtime.default.unsafe.run(handler(event)).getOrThrowFiberFailure()
        }
  end events

  private def liftEventIO[A](op: ExecutionContext ?=> scala.concurrent.Future[Either[TauriError, A]])(using Trace): IO[TauriError, A] =
    ZIO.fromFuture(ec => op(using ec)).absolve.mapError(TauriError.fromThrowable)

  // ====================
  // Extensions for Resource Lifecycle
  // ====================

  extension (listener: PluginListener)
    /** Unregister this listener with ZIO encapsulation.
      *
      * @return IO with TauriError in the error channel
      */
    def unregisterZIO(using Trace): IO[TauriError, Unit] =
      ZIO.fromFuture(ec => listener.unregister()(using ec)).absolve.mapError(TauriError.fromThrowable)

  extension (resource: Resource)
    /** Close this resource with ZIO encapsulation.
      *
      * @return IO with TauriError in the error channel
      */
    def closeZIO(using Trace): IO[TauriError, Unit] =
      ZIO.fromFuture(ec => resource.close()(using ec)).absolve.mapError(TauriError.fromThrowable)
end zio
