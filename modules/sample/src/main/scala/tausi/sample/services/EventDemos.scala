/*
 * Copyright (c) 2025 Tausi contributors.
 * See LICENSE file for terms.
 */
package tausi.sample.services

import zio.*
import zio.stream.*

import tausi.api.Event
import tausi.api.EventMessage
import tausi.api.TauriError
import tausi.api.codec.*
import tausi.zio.events

/** Demonstrates Tausi's event system with ZIO integration.
  *
  * This module shows how to use Tauri's event system for communication between the frontend
  * (Scala.js) and the Rust backend using ZIO effects.
  *
  * Events are defined as type-safe `Event[A]` instances that couple the event name with its
  * payload type, ensuring compile-time verification that emit and listen sites agree on types.
  */
object EventDemos:

  // ==========================================================================
  // Event Payloads
  // ==========================================================================

  /** Event payload for survey events. */
  final case class SurveyEvent(eventType: String, data: String)

  object SurveyEvent:
    given Codec[SurveyEvent] = Codec.derived
    given CanEqual[SurveyEvent, SurveyEvent] = CanEqual.derived

  // ==========================================================================
  // Event Definitions (type-safe Event[A] instances)
  // ==========================================================================

  /** Survey event for frontend-backend communication. */
  given surveyEvent: Event[SurveyEvent] = Event.define("survey-event")

  /** Signal that the frontend is ready. */
  given frontendReady: Event[Unit] = Event.define0("frontend-ready")

  /** Signal that the backend is ready with a message. */
  given backendReady: Event[String] = Event.define("backend-ready")

  // ==========================================================================
  // Event Operations
  // ==========================================================================

  /** Listen for backend events using ZIO.
    *
    * This demonstrates the basic event listening pattern with ZIO's IO effect type. The listener
    * will receive events until the handle is unlistened.
    *
    * The handler receives an Either to properly handle decode errors as values.
    *
    * @param handler
    *   Callback function to handle incoming events or decode errors
    * @return
    *   IO effect that registers the listener and returns a handle
    */
  def listenForSurveyEvents(
      handler: Either[TauriError.EventError, EventMessage[SurveyEvent]] => Unit
  ): IO[TauriError, tausi.api.EventHandle] =
    events.listen(handler)

  /** Listen for backend events with simplified handler (success only).
    *
    * Convenience method that handles only successful decodes and logs errors.
    *
    * @param handler
    *   Callback function to handle successfully decoded events
    * @return
    *   IO effect that registers the listener and returns a handle
    */
  def listenForSurveyEventsSimple(
      handler: SurveyEvent => Unit
  ): IO[TauriError, tausi.api.EventHandle] =
    events.listen[SurveyEvent] {
      case Right(msg) => handler(msg.payload)
      case Left(err)  => org.scalajs.dom.console.error(s"Event decode error: ${err.message}")
    }

  /** Emit an event to the backend using ZIO.
    *
    * This demonstrates sending events from the frontend to the Rust backend using ZIO's effect
    * system. The event type is resolved from the implicit Event[SurveyEvent] instance.
    *
    * @param event
    *   The event payload to emit
    * @return
    *   IO effect that emits the event
    */
  def emitSurveyEvent(event: SurveyEvent): IO[TauriError, Unit] =
    events.emit(event)

  /** Notify the backend that the frontend is ready.
    *
    * This is a common pattern for coordinating startup between frontend and backend.
    * Uses the Event[Unit] instance for events without payload.
    *
    * @return
    *   IO effect that emits the ready event
    */
  def notifyFrontendReady: IO[TauriError, Unit] =
    events.emit(())

  /** Listen for the backend ready signal.
    *
    * This demonstrates using `once` to listen for a single event occurrence.
    * The handler receives an Either for proper error handling.
    *
    * @param onReady
    *   Callback to invoke when backend is ready (receives Either for error handling)
    * @return
    *   IO effect that registers the one-time listener
    */
  def awaitBackendReady(
      onReady: Either[TauriError.EventError, EventMessage[String]] => Unit
  ): IO[TauriError, tausi.api.EventHandle] =
    events.once(onReady)

  /** Listen for the backend ready signal with simplified handler.
    *
    * Convenience method for when you only care about successful events.
    *
    * @param onReady
    *   Callback to invoke when backend is ready
    * @return
    *   IO effect that registers the one-time listener
    */
  def awaitBackendReadySimple(onReady: String => Unit): IO[TauriError, tausi.api.EventHandle] =
    events.once[String] {
      case Right(msg) => onReady(msg.payload)
      case Left(err)  => org.scalajs.dom.console.error(s"Backend ready decode error: ${err.message}")
    }
end EventDemos
