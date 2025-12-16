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

import tausi.api.codec.Decoder
import tausi.api.codec.Encoder
import tausi.api.commands.event.Emit
import tausi.api.commands.event.EmitTo
import tausi.api.core.invoke

/** Event-system entry point mirroring `@tauri-apps/api/event`. */
object event:
  /** Register a persistent listener with default options. */
  def listen[T](name: String, handler: EventMessage[T] => Unit)(using ExecutionContext, Decoder[T]): Future[EventHandle] =
    listen(name, handler, EventOptions.default)

  /** Register a persistent listener for a predefined [[TauriEvent]]. */
  def listen[T](
    event: TauriEvent,
    handler: EventMessage[T] => Unit
  )(using ExecutionContext, Decoder[T]): Future[EventHandle] =
    listen(event.value, handler)

  /** Register a persistent listener with explicit options. */
  def listen[T](
    name: String,
    handler: EventMessage[T] => Unit,
    options: EventOptions
  )(using ExecutionContext, Decoder[T]): Future[EventHandle] =
    registerListener(name, handler, options, autoUnlisten = false)

  /** Register a persistent listener for a predefined [[TauriEvent]] with options. */
  def listen[T](
    event: TauriEvent,
    handler: EventMessage[T] => Unit,
    options: EventOptions
  )(using ExecutionContext, Decoder[T]): Future[EventHandle] =
    registerListener(event.value, handler, options, autoUnlisten = false)

  /** Register a once-off listener with default options. */
  def once[T](
    name: String,
    handler: EventMessage[T] => Unit
  )(using ExecutionContext, Decoder[T]): Future[EventHandle] =
    once(name, handler, EventOptions.default)

  /** Once-off listener for a predefined [[TauriEvent]]. */
  def once[T](
    event: TauriEvent,
    handler: EventMessage[T] => Unit
  )(using ExecutionContext, Decoder[T]): Future[EventHandle] =
    once(event.value, handler, EventOptions.default)

  /** Register a once-off listener with explicit options. */
  def once[T](
    name: String,
    handler: EventMessage[T] => Unit,
    options: EventOptions
  )(using ExecutionContext, Decoder[T]): Future[EventHandle] =
    registerListener(name, handler, options, autoUnlisten = true)

  /** Once-off listener for a predefined [[TauriEvent]] with options. */
  def once[T](
    event: TauriEvent,
    handler: EventMessage[T] => Unit,
    options: EventOptions
  )(using ExecutionContext, Decoder[T]): Future[EventHandle] =
    registerListener(event.value, handler, options, autoUnlisten = true)

  /** Emit an event without a payload. */
  def emit(
    name: String
  )(using ExecutionContext): Future[Unit] =
    val eventName = EventName.unsafe(name) // TauriEvent constants are always valid
    invoke(Emit(eventName, None))

  /** Emit a predefined [[TauriEvent]] without payload. */
  def emit(
    event: TauriEvent
  )(using ExecutionContext): Future[Unit] =
    invoke(Emit(EventName(event), None))

  /** Emit an event with a payload. */
  def emit[T](
    name: String,
    payload: T
  )(using ExecutionContext, Encoder[T]): Future[Unit] =
    val eventName = EventName.unsafe(name)
    invoke(Emit(eventName, Some(summon[Encoder[T]].encode(payload))))

  /** Emit a predefined [[TauriEvent]] with payload. */
  def emit[T](
    event: TauriEvent,
    payload: T
  )(using ExecutionContext, Encoder[T]): Future[Unit] =
    invoke(Emit(EventName(event), Some(summon[Encoder[T]].encode(payload))))

  /** Emit to a specific label target. */
  def emitTo(
    label: String,
    name: String
  )(using ExecutionContext): Future[Unit] =
    val eventName = EventName.unsafe(name)
    invoke(EmitTo(EventTarget.AnyLabel(label), eventName, None))

  /** Emit a predefined [[TauriEvent]] to a label. */
  def emitTo(
    label: String,
    event: TauriEvent
  )(using ExecutionContext): Future[Unit] =
    invoke(EmitTo(EventTarget.AnyLabel(label), EventName(event), None))

  /** Emit to an explicit target definition. */
  def emitTo(
    target: EventTarget,
    name: String
  )(using ExecutionContext): Future[Unit] =
    val eventName = EventName.unsafe(name)
    invoke(EmitTo(target, eventName, None))

  /** Emit a predefined [[TauriEvent]] to an explicit target. */
  def emitTo(
    target: EventTarget,
    event: TauriEvent
  )(using ExecutionContext): Future[Unit] =
    invoke(EmitTo(target, EventName(event), None))

  /** Emit with payload to a specific label. */
  def emitTo[T](
    label: String,
    name: String,
    payload: T
  )(using ExecutionContext, Encoder[T]): Future[Unit] =
    val eventName = EventName.unsafe(name)
    invoke(EmitTo(EventTarget.AnyLabel(label), eventName, Some(summon[Encoder[T]].encode(payload))))

  /** Emit a predefined [[TauriEvent]] with payload to a label. */
  def emitTo[T](
    label: String,
    event: TauriEvent,
    payload: T
  )(using ExecutionContext, Encoder[T]): Future[Unit] =
    invoke(EmitTo(EventTarget.AnyLabel(label), EventName(event), Some(summon[Encoder[T]].encode(payload))))

  /** Emit with payload to an explicit target. */
  def emitTo[T](
    target: EventTarget,
    name: String,
    payload: T
  )(using ExecutionContext, Encoder[T]): Future[Unit] =
    val eventName = EventName.unsafe(name)
    invoke(EmitTo(target, eventName, Some(summon[Encoder[T]].encode(payload))))

  /** Emit a predefined [[TauriEvent]] with payload to an explicit target. */
  def emitTo[T](
    target: EventTarget,
    event: TauriEvent,
    payload: T
  )(using ExecutionContext, Encoder[T]): Future[Unit] =
    invoke(EmitTo(target, EventName(event), Some(summon[Encoder[T]].encode(payload))))

  /** Unlisten using a previously obtained handle. */
  def unlisten(
    handle: EventHandle
  )(using ExecutionContext): Future[Unit] =
    unlistenInternal(handle.event, handle.eventId, handle.callbackId)

  private def registerListener[T](
    name: String,
    handler: EventMessage[T] => Unit,
    options: EventOptions,
    autoUnlisten: Boolean
  )(using ec: ExecutionContext, decoder: Decoder[T]): Future[EventHandle] =
    import tausi.api.internal.TauriInternalsGlobal

    // scalafix:off
    var callbackId: CallbackId = CallbackId.unsafe(-1)
    val jsHandler: js.Function1[js.Dynamic, Unit] = (raw: js.Dynamic) =>
      val rawPayload = raw.selectDynamic("payload")
      val eventId = EventId.unsafe(raw.selectDynamic("id").asInstanceOf[Int])
      val eventName = raw.selectDynamic("event").asInstanceOf[String]
      decoder.decode(rawPayload) match
        case Right(payload) =>
          val event: EventMessage[T] = EventMessage(eventName, eventId, payload)
          if autoUnlisten then
            val _ = unlistenInternal(eventName, eventId, callbackId).onComplete(_ => ())
          handler(event)
        case Left(err) =>
          // Log decode error - in production might want structured error handling
          val _: Unit = scalajs.js.Dynamic.global.console.error(s"Failed to decode event payload for '$eventName': $err").asInstanceOf[Unit]
    // scalafix:on
    val rawId = TauriInternalsGlobal.transformCallback(jsHandler, false)
    callbackId = CallbackId.unsafe(rawId)

    // Note: We must use raw IPC for listen because it requires callback function registration
    // which cannot be represented in the Command typeclass system
    import tausi.api.internal.{TauriInternalsGlobal as TIG, InvokeOptionsJS}
    val eventName = EventName.unsafe(name) // User-provided names validated at runtime by Tauri

    val args = js.Dictionary[Any](
      "event" -> eventName.value,
      "target" -> options.target.toJS,
      "handler" -> callbackId.toInt
    )

    val jsPromise = TIG.invoke[Int]("plugin:event|listen", args, InvokeOptionsJS.empty)
    jsPromise.toFuture.map { eventIdentifier =>
      EventHandle(name, EventId.unsafe(eventIdentifier), callbackId)
    }
  end registerListener

  private def unlistenInternal(
    name: String,
    eventId: EventId,
    callbackId: CallbackId
  )(using ExecutionContext): Future[Unit] =
    import tausi.api.internal.{EventPluginInternalsBridge, TauriInternalsGlobal}
    import tausi.api.commands.event.Unlisten

    val handled = EventPluginInternalsBridge.unregisterListener(name, eventId.toInt)
    if !handled then TauriInternalsGlobal.unregisterCallback(callbackId.toInt)

    // Use the generated Unlisten command
    val eventName = EventName.unsafe(name)
    invoke(Unlisten(eventName, eventId))
  end unlistenInternal
end event
