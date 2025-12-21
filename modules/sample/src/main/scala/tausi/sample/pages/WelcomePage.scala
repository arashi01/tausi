/*
 * Copyright (c) 2025 Tausi contributors.
 * See LICENSE file for terms.
 */
package tausi.sample.pages

import com.raquo.laminar.api.L.*

import tausi.sample.components.*
import tausi.sample.state.AppState

/** Welcome page - first step of the survey wizard. */
object WelcomePage:
  def apply(state: AppState): HtmlElement =
    Layout.card(
      Layout.pageHeader(
        "Welcome to the Customer Survey",
        Some("Help us improve our products and services")
      ),
      div(
        cls := "space-y-4 text-text-secondary",
        p(
          "Thank you for taking the time to complete this survey. Your feedback is valuable ",
          "and helps us understand how we can better serve you."
        ),
        p("This survey takes approximately ", strong("2-3 minutes"), " to complete."),
        div(
          cls := "bg-surface-700 rounded-lg p-4 mt-6",
          h3(cls := "text-text-primary font-medium mb-2", "What to expect:"),
          ul(
            cls := "list-disc list-inside space-y-1 text-sm",
            li("Contact information (name, email, company)"),
            li("Satisfaction and recommendation ratings"),
            li("Feature preferences and feedback"),
            li("Review and submit")
          )
        ),
        div(
          cls := "bg-primary/10 border border-primary/30 rounded-lg p-4 mt-4",
          div(cls := "flex items-start gap-3"),
          div(
            cls := "text-primary",
            svg.svg(
              svg.cls := "w-5 h-5 mt-0.5",
              svg.viewBox := "0 0 24 24",
              svg.fill := "none",
              svg.stroke := "currentColor",
              svg.strokeWidth := "2",
              svg.path(
                svg.d := "M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z",
                svg.strokeLineCap := "round",
                svg.strokeLineJoin := "round"
              )
            )
          ),
          p(
            cls := "text-sm",
            "Your responses are saved locally to your device using Tauri's secure file storage."
          )
        )
      ),
      // Navigation
      div(
        cls := "flex justify-end mt-8",
        Buttons.primary("Get Started", () => state.navigateNext())
      )
    )
