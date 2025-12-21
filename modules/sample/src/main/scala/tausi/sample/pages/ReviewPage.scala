/*
 * Copyright (c) 2025 Tausi contributors.
 * See LICENSE file for terms.
 */
package tausi.sample.pages

import com.raquo.laminar.api.L.*

import tausi.sample.components.*
import tausi.sample.state.AppState

/** Review page - shows summary before submission. */
object ReviewPage:
  def apply(state: AppState): HtmlElement =
    Layout.card(
      Layout.pageHeader("Review Your Responses", Some("Please verify your information before submitting")),
      div(
        cls := "space-y-6",
        // Contact Details Section
        Section(
          "Contact Information",
          div(
            cls := "grid grid-cols-2 gap-4",
            ReviewField("First Name", state.contactDetails.signal.map(_.firstName)),
            ReviewField("Last Name", state.contactDetails.signal.map(_.lastName)),
            ReviewField("Email", state.contactDetails.signal.map(_.email)),
            ReviewField("Company", state.contactDetails.signal.map(c => if c.company.isEmpty then "Not provided" else c.company))
          )
        ),
        // Ratings Section
        Section(
          "Your Ratings",
          div(
            cls := "grid grid-cols-2 gap-4",
            ReviewField(
              "Satisfaction",
              state.surveyAnswers.signal.map(a => a.satisfaction.map(r => s"$r / 5").getOrElse("Not rated"))
            ),
            ReviewField(
              "Recommendation",
              state.surveyAnswers.signal.map(a => a.recommendation.map(r => s"$r / 5").getOrElse("Not rated"))
            )
          )
        ),
        // Features Section
        Section(
          "Important Features",
          child <-- state.surveyAnswers.signal.map { answers =>
            if answers.features.isEmpty then
              p(cls := "text-text-muted italic", "No features selected")
            else
              div(
                cls := "flex flex-wrap gap-2",
                answers.features.map { feature =>
                  span(
                    cls := "px-3 py-1 bg-primary/20 text-primary rounded-full text-sm",
                    feature
                  )
                }
              )
          }
        ),
        // Feedback Section
        Section(
          "Additional Feedback",
          child <-- state.surveyAnswers.signal.map { answers =>
            if answers.feedback.isEmpty then
              p(cls := "text-text-muted italic", "No feedback provided")
            else
              p(cls := "text-text-secondary", answers.feedback)
          }
        )
      ),
      // Warning
      div(
        cls := "bg-warning/10 border border-warning/30 rounded-lg p-4 mt-6",
        p(
          cls := "text-sm text-warning",
          "By submitting, your responses will be saved to a file on your device."
        )
      ),
      // Navigation
      div(
        cls := "flex justify-between mt-8",
        Buttons.secondary("Back", () => state.navigatePrevious()),
        Buttons.primary("Submit Survey", () => state.navigateNext())
      )
    )

  private def Section(title: String, content: Modifier[HtmlElement]*): HtmlElement =
    div(
      cls := "bg-surface-700 rounded-lg p-4",
      h3(cls := "text-text-primary font-medium mb-3", title),
      content
    )

  private def ReviewField(label: String, valueSignal: Signal[String]): HtmlElement =
    div(
      div(cls := "text-xs text-text-muted uppercase tracking-wide", label),
      div(cls := "text-text-primary font-medium", child.text <-- valueSignal)
    )
