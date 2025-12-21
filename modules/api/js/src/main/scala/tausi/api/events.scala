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
import tausi.api.commands.event.Emit
import tausi.api.commands.event.EmitTo
import tausi.api.core.invoke

/** Type-safe event-system entry point mirroring `@tauri-apps/api/event`.
  *
  * This module provides type-safe event operations using the [[Event]] typeclass. Event names and
  * payload types are coupled at the type level, ensuring compile-time verification that emit and
  * listen sites agree on payload types.
  *
  * == Error Handling ==
  *
  * Decoding errors are represented as values via `Either[TauriError.EventError, EventMessage[A]]`.
  * This follows the principle of errors-as-values and allows callers to handle decode failures
  * explicitly.
  *
  * For convenience, unsafe variants are provided that take a separate `onError` callback.
  *
  * @example
  *   {{{
  * import tausi.api.Event
  * import tausi.api.events
  * import tausi.api.codec.Codec
  *
  * // Define event with payload type
  * final case class SurveySubmission(data: String) derives Codec
  * given surveySubmitted: Event[SurveySubmission] = Event.define("survey-submitted")
  *
  * // Type-safe listen with error handling
  * events.listen {
  *   case Right(msg) => println(msg.payload.data)
  *   case Left(err) => println(s"Decode error: ${err.message}")
  * }
  *
  * // Type-safe emit - payload type verified against Event instance
  * events.emit(SurveySubmission("test"))
  *   }}}
  */
object events:
  // ============================================================
  // Type-Safe Event API (Event[A] based)
  // ============================================================

  /** Register a persistent listener for an event with default options.
    *
    * The handler receives an Either to handle both successful decodes and decode failures.
    * This follows the errors-as-values principle for proper error handling.
    *
    * @param handler
    *   Callback receiving Either decode error or decoded event message
    * @param ev
    *   The Event instance defining name and payload type
    * @return
    *   Future containing the event handle for unlistening
    *
    * @example
    *   {{{
    * given surveySubmitted: Event[SurveySubmission] = Event.define("survey-submitted")
    *
    * events.listen {
    *   case Right(msg) => println(msg.payload)
    *   case Left(err) => println(s"Decode error: ${err.message}")
    * }
    *   }}}
    */
  def listen[A](handler: Either[TauriError.EventError, EventMessage[A]] => Unit)(using
    ev: Event[A],
    ec: ExecutionContext
  ): Future[EventHandle] =
    listen(handler, EventOptions.default)

  /** Register a persistent listener with explicit options.
    *
    * @param handler
    *   Callback receiving Either decode error or decoded event message
    * @param options
    *   Event listening options (e.g., target filter)
    * @param ev
    *   The Event instance defining name and payload type
    * @return
    *   Future containing the event handle for unlistening
    */
  def listen[A](handler: Either[TauriError.EventError, EventMessage[A]] => Unit, options: EventOptions)(using
    ev: Event[A],
    ec: ExecutionContext
  ): Future[EventHandle] =
    registerListener(ev.name.value, handler, options, autoUnlisten = false)(using ec, ev.decoder)

  /** Register a once-off listener with default options.
    *
    * The listener automatically unregisters after receiving the first event.
    * The handler receives an Either to handle both successful decodes and decode failures.
    *
    * @param handler
    *   Callback receiving Either decode error or decoded event message
    * @param ev
    *   The Event instance defining name and payload type
    * @return
    *   Future containing the event handle
    */
  def once[A](handler: Either[TauriError.EventError, EventMessage[A]] => Unit)(using
    ev: Event[A],
    ec: ExecutionContext
  ): Future[EventHandle] =
    once(handler, EventOptions.default)

  /** Register a once-off listener with explicit options.
    *
    * @param handler
    *   Callback receiving Either decode error or decoded event message
    * @param options
    *   Event listening options
    * @param ev
    *   The Event instance defining name and payload type
    * @return
    *   Future containing the event handle
    */
  def once[A](handler: Either[TauriError.EventError, EventMessage[A]] => Unit, options: EventOptions)(using
    ev: Event[A],
    ec: ExecutionContext
  ): Future[EventHandle] =
    registerListener(ev.name.value, handler, options, autoUnlisten = true)(using ec, ev.decoder)

  /** Emit an event with a payload.
    *
    * The event name and encoder are resolved from the implicit [[Event]] instance.
    *
    * @param payload
    *   The payload to emit
    * @param ev
    *   The Event instance defining name and payload type
    * @return
    *   Future completing when the event is emitted
    *
    * @example
    *   {{{
    * given surveySubmitted: Event[SurveySubmission] = Event.define("survey-submitted")
    *
    * event.emit(SurveySubmission("test data"))
    *   }}}
    */
  def emit[A](payload: A)(using
    ev: Event[A],
    ec: ExecutionContext
  ): Future[Unit] =
    invoke(Emit(ev.name, Some(ev.encoder.encode(payload))))

  /** Emit an event to a specific target.
    *
    * @param target
    *   The target to emit to
    * @param payload
    *   The payload to emit
    * @param ev
    *   The Event instance defining name and payload type
    * @return
    *   Future completing when the event is emitted
    */
  def emitTo[A](target: EventTarget, payload: A)(using
    ev: Event[A],
    ec: ExecutionContext
  ): Future[Unit] =
    invoke(EmitTo(target, ev.name, Some(ev.encoder.encode(payload))))

  /** Emit an event to a specific label.
    *
    * @param label
    *   The label to emit to
    * @param payload
    *   The payload to emit
    * @param ev
    *   The Event instance defining name and payload type
    * @return
    *   Future completing when the event is emitted
    */
  def emitTo[A](label: String, payload: A)(using
    ev: Event[A],
    ec: ExecutionContext
  ): Future[Unit] =
    invoke(EmitTo(EventTarget.AnyLabel(label), ev.name, Some(ev.encoder.encode(payload))))

  /** Unlisten using a previously obtained handle.
    *
    * @param handle
    *   The event handle from a previous listen call
    * @return
    *   Future completing when unlistening is complete
    */
  def unlisten(handle: EventHandle)(using ExecutionContext): Future[Unit] =
    unlistenInternal(handle.event, handle.eventId, handle.callbackId)

  // ============================================================
  // Internal Implementation
  // ============================================================

  private def registerListener[T](
    name: String,
    handler: Either[TauriError.EventError, EventMessage[T]] => Unit,
    options: EventOptions,
    autoUnlisten: Boolean
  )(using ec: ExecutionContext, decoder: Decoder[T]): Future[EventHandle] =
    import scala.util.Failure
    import scala.util.Success

    import tausi.api.internal.TauriInternalsGlobal

    // scalafix:off
    var callbackId: CallbackId = CallbackId.unsafe(-1)
    val jsHandler: js.Function1[js.Dynamic, Unit] = (raw: js.Dynamic) =>
      val rawPayload = raw.selectDynamic("payload")
      val eventId = EventId.unsafe(raw.selectDynamic("id").asInstanceOf[Int])
      val eventName = raw.selectDynamic("event").asInstanceOf[String]
      val result: Either[TauriError.EventError, EventMessage[T]] = decoder.decode(rawPayload) match
        case Right(payload) =>
          val event: EventMessage[T] = EventMessage(eventName, eventId, payload)
          if autoUnlisten then
            // Unlisten failure is a defect - the system is in an inconsistent state
            // We throw to surface this as an unhandled exception rather than silently ignore
            unlistenInternal(eventName, eventId, callbackId).onComplete {
              case Failure(e) =>
                throw TauriError.EventError(eventName, s"Failed to auto-unlisten: ${e.getMessage}", Some(e))
              case Success(_) => ()
            }
          Right(event)
        case Left(err) =>
          val errorMsg = s"Failed to decode event payload: $err"
          Left(TauriError.EventError(eventName, errorMsg))
      handler(result)
    // scalafix:on
    val rawId = TauriInternalsGlobal.transformCallback(jsHandler, false)
    callbackId = CallbackId.unsafe(rawId)

    // Note: We must use raw IPC for listen because it requires callback function registration
    // which cannot be represented in the Command typeclass system
    import tausi.api.internal.{InvokeOptionsJS, TauriInternalsGlobal as TIG}
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
    import tausi.api.commands.event.Unlisten
    import tausi.api.internal.{EventPluginInternalsBridge, TauriInternalsGlobal}

    val handled = EventPluginInternalsBridge.unregisterListener(name, eventId.toInt)
    if !handled then TauriInternalsGlobal.unregisterCallback(callbackId.toInt)

    // Use the generated Unlisten command
    val eventName = EventName.unsafe(name)
    invoke(Unlisten(eventName, eventId))
  end unlistenInternal
end events
