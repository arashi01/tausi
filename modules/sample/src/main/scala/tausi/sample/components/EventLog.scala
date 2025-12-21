/*
 * Copyright (c) 2025 Tausi contributors.
 * See LICENSE file for terms.
 */
package tausi.sample.components

import com.raquo.laminar.api.L.*

import zio.Runtime

import tausi.api.EventMessage
import tausi.laminar.*
import tausi.sample.events.SurveyEvents.{given, *}
import tausi.sample.model.*
import tausi.zio.*
import tausi.zio.ZStreamIO.given

/** Event log panel demonstrating stream-to-Laminar integration.
  *
  * This component showcases the key Tausi pattern for converting effect
  * streams to Laminar signals using the [[StreamSource]] typeclass and
  * [[StreamState]] ADT.
  *
  * ==Pattern Demonstrated==
  * {{{
  * // 1. Import the stream source for your effect system
  * import tausi.zio.ZStreamIO.given
  *
  * // 2. Import the Laminar extensions
  * import tausi.laminar.*
  *
  * // 3. Convert the stream to a Laminar Signal with full lifecycle visibility
  * val stateSignal: Signal[StreamState[EventMessage[T]]] =
  *   events.stream[T].toStateSignal
  *
  * // 4. Pattern match on StreamState for comprehensive UI feedback
  * child <-- stateSignal.map {
  *   case StreamState.Running => div("Waiting for events...")
  *   case StreamState.Value(msg) => renderEvent(msg)
  *   case StreamState.Failed(err) => renderError(err)
  *   case StreamState.Completed => div("Stream completed")
  *   case StreamState.CompletedWith(last) => renderFinal(last)
  * }
  * }}}
  *
  * The [[StreamState]] ADT provides visibility into all lifecycle phases:
  *   - `Running`: Stream active, no values received yet (loading state)
  *   - `Value(a)`: Stream emitted a value (may emit more)
  *   - `Failed(err)`: Stream terminated with an error
  *   - `Completed`: Stream completed with no final value
  *   - `CompletedWith(a)`: Stream completed with a final value
  */
object EventLog:
  given Runtime[Any] = Runtime.default

  def apply(): HtmlElement =
    // State var to hold the current stream state
    val stateVar: Var[StreamState[EventMessage[SurveySubmittedEvent]]] =
      Var(StreamState.Running)

    div(
      cls := "fixed bottom-4 right-4 w-80 bg-surface-800 border border-border rounded-lg shadow-lg overflow-hidden",
      // Set up stream subscription when mounted
      onMountUnmountCallback(
        mount = ctx => {
          given Owner = ctx.owner

          org.scalajs.dom.console.log("[EventLog] Component mounted, setting up stream subscription")

          // Create ZStream for survey submission events
          // This stream emits EventMessage[SurveySubmittedEvent] for each event
          val submissionStream = events.stream[SurveySubmittedEvent]

          org.scalajs.dom.console.log("[EventLog] Created submissionStream, calling toStateSignal")

          // Convert to Laminar Signal with full lifecycle state
          // StreamSource[ZStreamIO] instance (from tausi.zio.ZStreamIO.given)
          // bridges ZIO streams to Laminar observables
          val stateSignal: Signal[StreamState[EventMessage[SurveySubmittedEvent]]] =
            submissionStream.toStateSignal

          org.scalajs.dom.console.log("[EventLog] toStateSignal returned, subscribing to signal")

          // Forward stream state to our Var
          stateSignal.foreach { state =>
            org.scalajs.dom.console.log(s"[EventLog] Signal state changed: $state")
            stateVar.set(state)
          }

          org.scalajs.dom.console.log("[EventLog] Stream subscription complete")
        },
        unmount = _ => {
          org.scalajs.dom.console.log("[EventLog] Component unmounted")
        }
      ),
      // Header
      div(
        cls := "bg-surface-700 px-4 py-2 border-b border-border flex items-center justify-between",
        div(
          cls := "flex items-center gap-2",
          div(cls := "w-2 h-2 rounded-full bg-success animate-pulse"),
          span(cls := "text-sm font-medium text-text-primary", "Event Stream")
        ),
        span(cls := "text-xs text-text-muted", "Live")
      ),
      // Stream content - renders based on stream state
      div(
        cls := "p-4 max-h-48 overflow-y-auto",
        child <-- stateVar.signal.map(renderStreamState)
      ),
      // Footer with pattern documentation
      div(
        cls := "bg-surface-700/50 px-4 py-2 border-t border-border",
        pre(
          cls := "text-xs text-text-muted font-mono overflow-x-auto",
          "stream.toStateSignal"
        )
      )
    )

  /** Render the stream state with appropriate UI feedback.
    *
    * This demonstrates the recommended pattern for handling all
    * [[StreamState]] variants with exhaustive pattern matching.
    */
  private def renderStreamState(state: StreamState[EventMessage[SurveySubmittedEvent]]): HtmlElement =
    state match
      case StreamState.Running =>
        // Loading state - stream active but no events yet
        div(
          cls := "flex items-center gap-2 text-text-muted",
          div(cls := "w-4 h-4 border-2 border-text-muted border-t-transparent rounded-full animate-spin"),
          span(cls := "text-sm", "Waiting for events...")
        )

      case StreamState.Value(msg) =>
        // Received an event - stream still active
        renderEvent(msg.payload)

      case StreamState.Failed(err) =>
        // Stream terminated with error
        div(
          cls := "text-error text-sm",
          span(cls := "font-medium", "Stream error: "),
          span(err.message)
        )

      case StreamState.Completed =>
        // Stream completed without final value
        div(
          cls := "text-text-muted text-sm italic",
          "Stream completed"
        )

      case StreamState.CompletedWith(msg) =>
        // Stream completed with final value
        div(
          cls := "space-y-2",
          renderEvent(msg.payload),
          div(
            cls := "text-xs text-text-muted italic border-t border-border pt-2",
            "Stream completed"
          )
        )

  private def renderEvent(event: SurveySubmittedEvent): HtmlElement =
    val statusClass =
      if event.success then "w-2 h-2 rounded-full bg-success"
      else "w-2 h-2 rounded-full bg-error"
    val statusText =
      if event.success then "Survey Submitted" else "Submission Failed"

    div(
      cls := "bg-surface-700 rounded p-2 text-sm",
      div(
        cls := "flex items-center gap-2 mb-1",
        div(cls := statusClass),
        span(cls := "font-medium text-text-primary", statusText)
      ),
      event.filePath.map { path =>
        div(
          cls := "text-xs text-text-muted font-mono truncate",
          path.split("/").lastOption.getOrElse(path)
        )
      }.getOrElse(emptyNode),
      event.errorMessage.map { err =>
        div(cls := "text-xs text-error", err)
      }.getOrElse(emptyNode)
    )
