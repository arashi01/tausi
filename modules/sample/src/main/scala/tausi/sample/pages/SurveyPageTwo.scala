/*
 * Copyright (c) 2025 Tausi contributors.
 * See LICENSE file for terms.
 */
package tausi.sample.pages

import com.raquo.laminar.api.L.*

import tausi.sample.components.*
import tausi.sample.config.SurveyConfig
import tausi.sample.state.AppState

/** Survey page two view - demonstrates reactive dependent fields using Laminar signals. */
object SurveyPageTwo:

  // Field IDs for the dynamic cascade
  private val IndustryId = "industry"
  private val ProductId = "product_line"
  private val FeedbackTopicId = "feedback_topic"

  def apply(state: AppState): HtmlElement =
    // Derived signals for the cascading dropdowns
    val industrySignal: Signal[Option[String]] =
      state.surveyAnswers.signal.map(_.get(IndustryId).filter(_.nonEmpty))

    val productSignal: Signal[Option[String]] =
      state.surveyAnswers.signal.map(_.get(ProductId).filter(_.nonEmpty))

    // Available products depend on selected industry
    val availableProducts: Signal[List[String]] =
      industrySignal.map {
        case Some(industry) => SurveyConfig.getProducts(industry)
        case None           => List.empty
      }

    // Available feedback topics depend on industry + product
    val availableFeedbackTopics: Signal[List[String]] =
      industrySignal.combineWith(productSignal).map {
        case (Some(industry), Some(product)) => SurveyConfig.getFeedbackTopics(industry, product)
        case _                               => List.empty
      }

    // Validation: all required dynamic fields answered
    val dynamicFieldsValid: Signal[Boolean] =
      state.surveyAnswers.signal.map { answers =>
        val hasIndustry = answers.get(IndustryId).exists(_.nonEmpty)
        val hasProduct = answers.get(ProductId).exists(_.nonEmpty)
        val hasTopic = answers.get(FeedbackTopicId).exists(_.nonEmpty)
        hasIndustry && hasProduct && hasTopic
      }

    // Validation: static required fields answered
    val staticFieldsValid: Signal[Boolean] =
      state.surveyAnswers.signal.map { answers =>
        SurveyConfig.pageTwoQuestions.filter(_.required).forall { q =>
          answers.get(q.id).exists(_.nonEmpty)
        }
      }

    val allValid: Signal[Boolean] =
      dynamicFieldsValid.combineWith(staticFieldsValid).map { case (d, s) => d && s }

    // Clear dependent fields when parent changes
    def onIndustryChange(value: String): Unit =
      state.setAnswer(IndustryId, value)
      state.clearAnswer(ProductId)
      state.clearAnswer(FeedbackTopicId)

    def onProductChange(value: String): Unit =
      state.setAnswer(ProductId, value)
      state.clearAnswer(FeedbackTopicId)

    Layout.card(
      Layout.sectionHeader(
        "Product Feedback",
        Some("Help us understand your experience with our solutions.")
      ),
      div(
        cls := "space-y-5",
        // Industry selector (static options)
        FormInputs.multiChoiceInput(
          id = IndustryId,
          labelText = "What industry are you in?",
          options = SurveyConfig.industries,
          valueSignal = industrySignal,
          onValueChange = onIndustryChange
        ),
        // Product selector (options depend on industry)
        child.maybe <-- availableProducts.map { products =>
          Option.when(products.nonEmpty)(
            div(
              cls := "animate-in fade-in duration-300",
              FormInputs.multiChoiceInput(
                id = ProductId,
                labelText = "Which product line do you use most?",
                options = products,
                valueSignal = productSignal,
                onValueChange = onProductChange
              )
            )
          )
        },
        // Feedback topic (options depend on industry + product)
        child.maybe <-- availableFeedbackTopics.map { topics =>
          Option.when(topics.nonEmpty)(
            div(
              cls := "animate-in fade-in duration-300",
              FormInputs.multiChoiceInput(
                id = FeedbackTopicId,
                labelText = "What area would you like to give feedback on?",
                options = topics,
                valueSignal = state.surveyAnswers.signal.map(_.get(FeedbackTopicId)),
                onValueChange = v => state.setAnswer(FeedbackTopicId, v)
              )
            )
          )
        },
        // Static questions
        div(
          cls := "pt-4 border-t border-border/50",
          SurveyConfig.pageTwoQuestions.map(QuestionRenderers.render(_, state))
        )
      ),
      Layout.buttonGroup(
        Buttons.secondary("Back", () => state.navigatePrevious()),
        Buttons.primary(
          "Review & Submit",
          () => state.navigateNext(),
          allValid.map(!_)
        )
      )
    )
end SurveyPageTwo
