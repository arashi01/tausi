/*
 * Copyright (c) 2025 Tausi contributors.
 * See LICENSE file for terms.
 */
package tausi.sample.components

import com.raquo.laminar.api.L.*

import tausi.sample.model.{QuestionType, SurveyQuestion}
import tausi.sample.state.AppState

/** Renders survey questions based on their type.
  *
  * Centralizes the mapping from `SurveyQuestion` to appropriate form input components,
  * eliminating duplication across survey pages.
  */
object QuestionRenderers:

  /** Render a survey question using the appropriate form input component.
    *
    * @param question The question configuration
    * @param state The application state for reading/writing answers
    * @return An HtmlElement for the rendered question
    */
  def render(question: SurveyQuestion, state: AppState): HtmlElement =
    question.questionType match
      case QuestionType.Rating =>
        FormInputs.ratingInput(
          id = question.id,
          labelText = question.text,
          valueSignal = state.surveyAnswers.signal.map(_.get(question.id)),
          onValueChange = v => state.setAnswer(question.id, v)
        )

      case QuestionType.Text =>
        FormInputs.textAreaInput(
          id = question.id,
          labelText = question.text,
          placeholderText = "Enter your response...",
          valueSignal = state.surveyAnswers.signal.map(_.getOrElse(question.id, "")),
          onValueChange = v => state.setAnswer(question.id, v),
          required = question.required
        )

      case QuestionType.MultiChoice =>
        FormInputs.multiChoiceInput(
          id = question.id,
          labelText = question.text,
          options = question.options.getOrElse(List.empty),
          valueSignal = state.surveyAnswers.signal.map(_.get(question.id)),
          onValueChange = v => state.setAnswer(question.id, v)
        )
end QuestionRenderers
