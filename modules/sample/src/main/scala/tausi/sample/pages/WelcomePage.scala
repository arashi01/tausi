/*
 * Copyright (c) 2025 Tausi contributors.
 * See LICENSE file for terms.
 */
package tausi.sample.pages

import com.raquo.laminar.api.L.*
import tausi.sample.components.*
import tausi.sample.config.SurveyConfig
import tausi.sample.state.AppState

/** Welcome page view with dark corporate theme. */
object WelcomePage:

  def apply(state: AppState): HtmlElement =
    Layout.card(
      Layout.hero(
        iconContent = clipboardIcon,
        title = SurveyConfig.surveyTitle,
        description = SurveyConfig.surveyDescription
      ),
      div(
        cls := "space-y-3 mt-6 sm:mt-8",
        featureItem("Quick", "Takes only 3-5 minutes to complete"),
        featureItem("Confidential", "Your responses are kept private"),
        featureItem("Impactful", "Helps us serve you better")
      ),
      div(
        cls := "text-center mt-6 sm:mt-8",
        Buttons.primary("Begin Survey", () => state.navigateNext())
      )
    )

  private def featureItem(title: String, description: String): HtmlElement =
    div(
      cls := "flex items-start gap-3 sm:gap-4 p-3 sm:p-4 bg-surface-700/50 rounded-lg border border-border/50",
      div(
        cls := "w-8 h-8 bg-primary/20 rounded-full flex items-center justify-center flex-shrink-0",
        checkIcon
      ),
      div(
        h3(cls := "font-semibold text-text-primary", title),
        p(cls := "text-sm text-text-secondary", description)
      )
    )

  private def checkIcon: Element =
    svg.svg(
      svg.cls := "w-4 h-4 text-primary",
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

  private def clipboardIcon: Element =
    svg.svg(
      svg.cls := "w-8 h-8 sm:w-10 sm:h-10 text-primary",
      svg.viewBox := "0 0 24 24",
      svg.fill := "none",
      svg.stroke := "currentColor",
      svg.strokeWidth := "1.5",
      svg.path(
        svg.d := "M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4",
        svg.strokeLineCap := "round",
        svg.strokeLineJoin := "round"
      )
    )
end WelcomePage
