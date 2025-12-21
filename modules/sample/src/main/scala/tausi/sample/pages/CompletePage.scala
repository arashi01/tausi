/*
 * Copyright (c) 2025 Tausi contributors.
 * See LICENSE file for terms.
 */
package tausi.sample.pages

import com.raquo.laminar.api.L.*

import zio.Runtime

import tausi.sample.commands.survey.{given, *}
import tausi.sample.components.*
import tausi.sample.events.SurveyEvents.{given, *}
import tausi.sample.model.*
import tausi.sample.state.AppState
import tausi.zio.*

/** Complete page - submits survey and shows result.
  *
  * This page demonstrates the key Tausi patterns:
  *   - Command invocation with `invoke(request).runWith(...)`
  *   - Event emission for notifications
  *   - Error handling with TauriError
  *   - Loading state management
  */
object CompletePage:
  given Runtime[Any] = Runtime.default

  def apply(state: AppState): HtmlElement =
    // Submit on mount if not already submitted
    div(
      onMountCallback { _ =>
        val hasResult = state.submissionResult.now().isDefined
        val hasError = state.submissionError.now().isDefined
        val isSubmitting = state.isSubmitting.now()

        if !hasResult && !hasError && !isSubmitting then submitSurvey(state)
      },
      Layout.card(
        // Dynamic content based on submission state
        child <-- state.isSubmitting.signal.combineWith(
          state.submissionResult.signal,
          state.submissionError.signal
        ).map {
          case (true, _, _)            => LoadingView()
          case (_, Some(result), _)    => SuccessView(result, state)
          case (_, _, Some(error))     => ErrorView(error, state)
          case (false, None, None)     => LoadingView() // Initial state, will trigger submit
        }
      )
    )

  /** Submit the survey using Tausi command invocation. */
  private def submitSurvey(state: AppState): Unit =
    state.isSubmitting.set(true)
    state.submissionError.set(None)

    // Build the submission
    val submission = SurveySubmission(
      contactDetails = state.contactDetails.now(),
      answers = state.surveyAnswers.now(),
      submittedAt = java.time.Instant.now().toString
    )

    // Invoke the save_survey command
    // This demonstrates the core Tausi pattern: type-safe command invocation
    // SaveSurveyRequest wraps the submission with a field name matching the
    // Rust function parameter for proper IPC serialization
    invoke(SaveSurveyRequest(submission)).runWith(
      onSuccess = response => {
        state.isSubmitting.set(false)
        state.submissionResult.set(Some(response))

        org.scalajs.dom.console.log("[CompletePage] Emitting success event")
        // Emit success event (demonstrates event emission)
        events.emit(SurveySubmittedEvent(
          success = true,
          filePath = Some(response.filePath),
          errorMessage = None
        )).runWith(
          onSuccess = _ => org.scalajs.dom.console.log("[CompletePage] Success event emitted successfully"),
          onError = err => org.scalajs.dom.console.error(s"[CompletePage] Failed to emit success event: ${err.message}")
        )
      },
      onError = err => {
        state.isSubmitting.set(false)
        state.submissionError.set(Some(err.message))

        org.scalajs.dom.console.log("[CompletePage] Emitting failure event")
        // Emit failure event
        events.emit(SurveySubmittedEvent(
          success = false,
          filePath = None,
          errorMessage = Some(err.message)
        )).runWith(
          onSuccess = _ => org.scalajs.dom.console.log("[CompletePage] Failure event emitted successfully"),
          onError = err => org.scalajs.dom.console.error(s"[CompletePage] Failed to emit failure event: ${err.message}")
        )
      }
    )

  private def LoadingView(): HtmlElement =
    div(
      cls := "text-center py-12",
      // Spinner
      div(
        cls := "inline-flex items-center justify-center w-16 h-16 rounded-full bg-primary/20 mb-6",
        div(cls := "w-8 h-8 border-2 border-primary border-t-transparent rounded-full animate-spin")
      ),
      h2(cls := "text-xl font-semibold text-text-primary mb-2", "Submitting Survey..."),
      p(cls := "text-text-secondary", "Saving your responses to file storage")
    )

  private def SuccessView(result: SaveSurveyResponse, state: AppState): HtmlElement =
    div(
      cls := "text-center py-8",
      // Success icon
      div(
        cls := "inline-flex items-center justify-center w-16 h-16 rounded-full bg-success/20 mb-6",
        svg.svg(
          svg.cls := "w-8 h-8 text-success",
          svg.viewBox := "0 0 24 24",
          svg.fill := "none",
          svg.stroke := "currentColor",
          svg.strokeWidth := "2",
          svg.path(
            svg.d := "M5 13l4 4L19 7",
            svg.strokeLineCap := "round",
            svg.strokeLineJoin := "round"
          )
        )
      ),
      h2(cls := "text-xl font-semibold text-text-primary mb-2", "Survey Submitted!"),
      p(cls := "text-text-secondary mb-6", result.message),
      // File location
      div(
        cls := "bg-surface-700 rounded-lg p-4 text-left mb-8",
        div(cls := "text-xs text-text-muted uppercase tracking-wide mb-1", "Saved to"),
        div(cls := "text-text-primary font-mono text-sm break-all", result.filePath)
      ),
      // Pattern demonstration note
      div(
        cls := "bg-primary/10 border border-primary/30 rounded-lg p-4 text-left mb-8",
        h3(cls := "text-primary font-medium mb-2", "Tausi Pattern Demonstrated"),
        pre(
          cls := "text-xs text-text-secondary font-mono overflow-x-auto",
          """invoke(SaveSurveyRequest(submission)).runWith(
  onSuccess = response => showSuccess(response),
  onError = err => showError(err.message)
)"""
        )
      ),
      // Start new survey button
      Buttons.primary("Start New Survey", () => state.reset())
    )

  private def ErrorView(error: String, state: AppState): HtmlElement =
    div(
      cls := "text-center py-8",
      // Error icon
      div(
        cls := "inline-flex items-center justify-center w-16 h-16 rounded-full bg-error/20 mb-6",
        svg.svg(
          svg.cls := "w-8 h-8 text-error",
          svg.viewBox := "0 0 24 24",
          svg.fill := "none",
          svg.stroke := "currentColor",
          svg.strokeWidth := "2",
          svg.path(
            svg.d := "M6 18L18 6M6 6l12 12",
            svg.strokeLineCap := "round",
            svg.strokeLineJoin := "round"
          )
        )
      ),
      h2(cls := "text-xl font-semibold text-text-primary mb-2", "Submission Failed"),
      p(cls := "text-text-secondary mb-4", "There was an error saving your survey."),
      // Error details
      div(
        cls := "bg-error/10 border border-error/30 rounded-lg p-4 text-left mb-8",
        div(cls := "text-xs text-error uppercase tracking-wide mb-1", "Error"),
        div(cls := "text-text-primary text-sm", error)
      ),
      // Actions
      div(
        cls := "flex justify-center gap-4",
        Buttons.secondary("Go Back", () => state.navigatePrevious()),
        Buttons.primary("Try Again", () => {
          state.submissionError.set(None)
          submitSurvey(state)
        })
      )
    )
