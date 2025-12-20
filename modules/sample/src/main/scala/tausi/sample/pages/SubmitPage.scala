/*
 * Copyright (c) 2025 Tausi contributors.
 * See LICENSE file for terms.
 */
package tausi.sample.pages

import com.raquo.laminar.api.L.*

import tausi.sample.components.*
import tausi.sample.config.SurveyConfig
import tausi.sample.model.*
import tausi.sample.services.SurveyService
import tausi.sample.state.AppState

/** Submit page view with review and submission.
  *
  * Uses ZIO-based SurveyService with proper error channel handling.
  * The TauriError message is displayed directly to users when submission fails.
  */
object SubmitPage:

  def apply(state: AppState, surveyService: SurveyService): HtmlElement =
    val isSubmitting = Var(false)
    val isSubmitted = Var(false)
    val errorMessage = Var(Option.empty[String])

    def handleSubmit(): Unit =
      isSubmitting.set(true)
      errorMessage.set(None)

      val submission = SurveySubmission(
        contactDetails = state.contactDetails.now(),
        answers = state.surveyAnswers.now(),
        submittedAt = new scalajs.js.Date().toISOString()
      )

      // Use ZIO effect with proper error channel
      val effect = surveyService.submitSurvey(submission)

      // Run effect with callbacks using the temporary utility
      // NOTE: This pattern should be simplified by tausi.zio utilities
      SurveyService.runEffect(
        effect,
        onSuccess = _ => {
          isSubmitting.set(false)
          isSubmitted.set(true)
        },
        onError = tauriError => {
          isSubmitting.set(false)
          // Display the TauriError.message directly - it contains user-friendly error text
          errorMessage.set(Some(tauriError.message))
        }
      )

    div(
      child <-- isSubmitted.signal.map { submitted =>
        if submitted then renderSuccess(state)
        else renderReview(state, isSubmitting, errorMessage, () => handleSubmit())
      }
    )

  private def renderSuccess(state: AppState): HtmlElement =
    Layout.card(
      Layout.successMessage(
        "Thank You!",
        "Your feedback has been submitted successfully. We appreciate your time and input."
      ),
      div(
        cls := "text-center mt-8",
        Buttons.primary("Start New Survey", () => state.reset())
      )
    )

  private def renderReview(
      state: AppState,
      isSubmitting: Var[Boolean],
      errorMessage: Var[Option[String]],
      onSubmit: () => Unit
  ): HtmlElement =
    Layout.card(
      Layout.sectionHeader(
        "Review Your Responses",
        Some("Please review your answers before submitting.")
      ),
      // Contact details summary
      div(
        cls := "bg-surface-700/50 rounded-lg p-4 mb-6 border border-border/50",
        h3(cls := "font-semibold text-text-primary mb-3", "Contact Information"),
        div(
          cls := "grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm",
          children <-- state.contactDetails.signal.map { contact =>
            List(
              summaryItem("First Name", contact.firstName),
              summaryItem("Last Name", contact.lastName),
              summaryItem("Phone", contact.phoneNumber)
            )
          }
        )
      ),
      // Survey answers summary
      div(
        cls := "space-y-3 mb-6",
        h3(cls := "font-semibold text-text-primary mb-3", "Survey Responses"),
        children <-- state.surveyAnswers.signal.map { answers =>
          SurveyConfig.surveyPages.flatMap { page =>
            page.questions.flatMap { q =>
              answers.get(q.id).filter(_.nonEmpty).map { answer =>
                answerSummaryItem(q.text, answer)
              }
            }
          }
        }
      ),
      // Error message
      child.maybe <-- errorMessage.signal.map {
        case Some(msg) =>
          Some(
            div(
              cls := "bg-error/10 border border-error/30 text-error px-4 py-3 rounded-lg mb-4",
              role := "alert",
              msg
            )
          )
        case None => None
      },
      Layout.buttonGroup(
        Buttons.secondary(
          "Back to Edit",
          () => state.navigatePrevious(),
          isSubmitting.signal
        ),
        child <-- isSubmitting.signal.map { submitting =>
          if submitting then
            button(
              cls := s"${Buttons.buttonBaseClasses} bg-primary/70 text-surface-900 flex items-center justify-center gap-2 cursor-wait",
              typ := "button",
              disabled := true,
              aria.busy := true,
              spinnerIcon,
              span("Submitting...")
            )
          else Buttons.primary("Submit Survey", onSubmit)
        }
      )
    )

  private def summaryItem(labelText: String, value: String): HtmlElement =
    div(
      span(cls := "text-text-muted", s"$labelText: "),
      span(cls := "text-text-primary font-medium", value)
    )

  private def answerSummaryItem(question: String, answer: String): HtmlElement =
    div(
      cls := "bg-surface-700/50 rounded-lg p-3 border border-border/50",
      p(cls := "text-sm text-text-secondary mb-1", question),
      p(cls := "text-text-primary", answer)
    )

  private def spinnerIcon: Element =
    svg.svg(
      svg.cls := "animate-spin h-5 w-5",
      svg.viewBox := "0 0 24 24",
      svg.fill := "none",
      svg.circle(
        svg.cls := "opacity-25",
        svg.cx := "12",
        svg.cy := "12",
        svg.r := "10",
        svg.stroke := "currentColor",
        svg.strokeWidth := "4"
      ),
      svg.path(
        svg.cls := "opacity-75",
        svg.fill := "currentColor",
        svg.d := "M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
      )
    )
end SubmitPage
