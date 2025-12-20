/*
 * Copyright (c) 2025 Tausi contributors.
 * See LICENSE file for terms.
 */
package tausi.sample.pages

import com.raquo.laminar.api.L.*

import tausi.sample.components.*
import tausi.sample.config.SurveyConfig
import tausi.sample.state.AppState

/** Survey page one view - rating questions. */
object SurveyPageOne:

  def apply(state: AppState): HtmlElement =
    val surveyPage = SurveyConfig.surveyPages.head

    val allAnswered: Signal[Boolean] =
      state.surveyAnswers.signal.map { answers =>
        surveyPage.questions.filter(_.required).forall { q =>
          answers.get(q.id).exists(_.nonEmpty)
        }
      }

    Layout.card(
      Layout.sectionHeader(
        surveyPage.title,
        Some(surveyPage.description)
      ),
      div(
        cls := "space-y-6",
        surveyPage.questions.map(QuestionRenderers.render(_, state))
      ),
      Layout.buttonGroup(
        Buttons.secondary("Back", () => state.navigatePrevious()),
        Buttons.primary(
          "Continue",
          () => state.navigateNext(),
          allAnswered.map(!_)
        )
      )
    )
end SurveyPageOne
