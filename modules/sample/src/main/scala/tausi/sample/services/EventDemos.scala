/*
 * Copyright (c) 2025 Tausi contributors.
 * See LICENSE file for terms.
 */
package tausi.sample.services

import zio.*
import zio.stream.*
import tausi.api.TauriError
import tausi.api.codec.*
import tausi.zio.events

/** Demonstrates Tausi's event system with ZIO integration.
  *
  * This module shows how to use Tauri's event system for communication between the frontend
  * (Scala.js) and the Rust backend using ZIO effects.
  */
object EventDemos:

  /** Event payload for survey events. */
  final case class SurveyEvent(eventType: String, data: String)

  object SurveyEvent:
    given Codec[SurveyEvent] = Codec.derived

  /** Listen for backend events using ZIO.
    *
    * This demonstrates the basic event listening pattern with ZIO's IO effect type. The listener
    * will receive events until the handle is unlistened.
    *
    * @param eventName
    *   Name of the event to listen for
    * @param handler
    *   Callback function to handle incoming events
    * @return
    *   IO effect that registers the listener and returns a handle
    */
  def listenForSurveyEvents(
      handler: SurveyEvent => Unit
  ): IO[TauriError, tausi.api.EventHandle] =
    events.listen[SurveyEvent](
      "survey-event",
      msg => handler(msg.payload)
    )

  /** Emit an event to the backend using ZIO.
    *
    * This demonstrates sending events from the frontend to the Rust backend using ZIO's effect
    * system.
    *
    * @param event
    *   The event payload to emit
    * @return
    *   IO effect that emits the event
    */
  def emitSurveyEvent(event: SurveyEvent): IO[TauriError, Unit] =
    events.emit("survey-event", event)

  /** Notify the backend that the frontend is ready.
    *
    * This is a common pattern for coordinating startup between frontend and backend.
    *
    * @return
    *   IO effect that emits the ready event
    */
  def notifyFrontendReady: IO[TauriError, Unit] =
    events.emit("frontend-ready", ())

  /** Listen for the backend ready signal.
    *
    * This demonstrates using `once` to listen for a single event occurrence.
    *
    * @param onReady
    *   Callback to invoke when backend is ready
    * @return
    *   IO effect that registers the one-time listener
    */
  def awaitBackendReady(onReady: String => Unit): IO[TauriError, tausi.api.EventHandle] =
    events.once[String](
      "backend-ready",
      msg => onReady(msg.payload)
    )
end EventDemos
