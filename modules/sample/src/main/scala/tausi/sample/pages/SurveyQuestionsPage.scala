/*
 * Copyright (c) 2025 Tausi contributors.
 * See LICENSE file for terms.
 */
package tausi.sample.pages

import com.raquo.laminar.api.L.*

import tausi.sample.components.*
import tausi.sample.state.AppState

/** Survey questions page - collects ratings and preferences. */
object SurveyQuestionsPage:
  private val featureOptions = List(
    "Performance" -> "Fast and responsive",
    "Reliability" -> "Stable and dependable",
    "Ease of Use" -> "Intuitive interface",
    "Documentation" -> "Clear guides and examples",
    "Support" -> "Helpful customer service",
    "Pricing" -> "Good value for money"
  )

  def apply(state: AppState): HtmlElement =
    Layout.card(
      Layout.pageHeader("Survey Questions", Some("Rate your experience")),
      div(
        cls := "space-y-8",
        // Satisfaction rating
        RatingQuestion(
          question = "How satisfied are you with our product overall?",
          description = "1 = Very Unsatisfied, 5 = Very Satisfied",
          valueSignal = state.surveyAnswers.signal.map(_.satisfaction),
          onSelect = rating => state.surveyAnswers.update(_.copy(satisfaction = Some(rating)))
        ),
        // Recommendation rating
        RatingQuestion(
          question = "How likely are you to recommend us to others?",
          description = "1 = Very Unlikely, 5 = Very Likely",
          valueSignal = state.surveyAnswers.signal.map(_.recommendation),
          onSelect = rating => state.surveyAnswers.update(_.copy(recommendation = Some(rating)))
        ),
        // Feature selection
        FeatureSelection(
          question = "Which features are most important to you?",
          description = "Select all that apply",
          options = featureOptions,
          selectedSignal = state.surveyAnswers.signal.map(_.features),
          onToggle = feature =>
            state.surveyAnswers.update { answers =>
              val newFeatures =
                if answers.features.contains(feature) then answers.features.filterNot(_ == feature)
                else answers.features :+ feature
              answers.copy(features = newFeatures)
            }
        ),
        // Feedback
        TextAreaQuestion(
          question = "Any additional feedback?",
          description = "Optional - share your thoughts",
          valueSignal = state.surveyAnswers.signal.map(_.feedback),
          onUpdate = feedback => state.surveyAnswers.update(_.copy(feedback = feedback)),
          placeholderText = "What could we do better? What do you love about our product?"
        )
      ),
      // Navigation
      div(
        cls := "flex justify-between mt-8",
        Buttons.secondary("Back", () => state.navigatePrevious()),
        Buttons.primary("Review Answers", () => state.navigateNext(), state.isSurveyComplete.map(!_))
      )
    )

  private def RatingQuestion(
      question: String,
      description: String,
      valueSignal: Signal[Option[Int]],
      onSelect: Int => Unit
  ): HtmlElement =
    div(
      cls := "space-y-3",
      div(
        h3(cls := "text-text-primary font-medium", question),
        p(cls := "text-sm text-text-muted", description)
      ),
      div(
        cls := "flex gap-2",
        (1 to 5).map { rating =>
          button(
            cls <-- valueSignal.map { selected =>
              val base = "w-12 h-12 rounded-lg font-medium transition-all"
              if selected.contains(rating) then s"$base bg-primary text-surface-900"
              else s"$base bg-surface-700 text-text-secondary hover:bg-surface-600"
            },
            rating.toString,
            onClick --> { _ => onSelect(rating) }
          )
        }
      )
    )

  private def FeatureSelection(
      question: String,
      description: String,
      options: List[(String, String)],
      selectedSignal: Signal[List[String]],
      onToggle: String => Unit
  ): HtmlElement =
    div(
      cls := "space-y-3",
      div(
        h3(cls := "text-text-primary font-medium", question),
        p(cls := "text-sm text-text-muted", description)
      ),
      div(
        cls := "grid grid-cols-2 gap-3",
        options.map { case (key, label) =>
          button(
            cls <-- selectedSignal.map { selected =>
              val base = "p-3 rounded-lg text-left transition-all border"
              if selected.contains(key) then s"$base bg-primary/20 border-primary text-text-primary"
              else s"$base bg-surface-700 border-border text-text-secondary hover:border-text-muted"
            },
            div(cls := "font-medium text-sm", key),
            div(cls := "text-xs opacity-75", label),
            onClick --> { _ => onToggle(key) }
          )
        }
      )
    )

  private def TextAreaQuestion(
      question: String,
      description: String,
      valueSignal: Signal[String],
      onUpdate: String => Unit,
      placeholderText: String
  ): HtmlElement =
    div(
      cls := "space-y-3",
      div(
        h3(cls := "text-text-primary font-medium", question),
        p(cls := "text-sm text-text-muted", description)
      ),
      textArea(
        cls := "w-full px-4 py-3 bg-surface-700 border border-border rounded-lg text-text-primary placeholder-text-muted focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary transition-colors resize-none",
        rows := 4,
        placeholder := placeholderText,
        controlled(
          value <-- valueSignal,
          onInput.mapToValue --> onUpdate
        )
      )
    )
