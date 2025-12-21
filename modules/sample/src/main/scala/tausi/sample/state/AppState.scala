/*
 * Copyright (c) 2025 Tausi contributors.
 * See LICENSE file for terms.
 */
package tausi.sample.state

import com.raquo.laminar.api.L.*

import tausi.sample.model.*

/** Application state container using Laminar's reactive primitives.
  *
  * Demonstrates the recommended pattern for managing state in a
  * Tausi + Laminar application.
  */
final case class AppState(
    currentPage: Var[Page],
    contactDetails: Var[ContactDetails],
    surveyAnswers: Var[SurveyAnswers],
    submissionResult: Var[Option[SaveSurveyResponse]],
    submissionError: Var[Option[String]],
    isSubmitting: Var[Boolean]
)

object AppState:
  /** Create initial application state. */
  def initial: AppState = AppState(
    currentPage = Var(Page.Welcome),
    contactDetails = Var(ContactDetails.empty),
    surveyAnswers = Var(SurveyAnswers.empty),
    submissionResult = Var(None),
    submissionError = Var(None),
    isSubmitting = Var(false)
  )

  extension (state: AppState)
    /** Navigate to the next page in the wizard. */
    def navigateNext(): Unit =
      state.currentPage.now().next.foreach(state.currentPage.set)

    /** Navigate to the previous page in the wizard. */
    def navigatePrevious(): Unit =
      state.currentPage.now().previous.foreach(state.currentPage.set)

    /** Navigate to a specific page. */
    def navigateTo(page: Page): Unit =
      state.currentPage.set(page)

    /** Check if contact details are complete. */
    def isContactComplete: Signal[Boolean] =
      state.contactDetails.signal.map(_.isComplete)

    /** Check if survey answers are complete. */
    def isSurveyComplete: Signal[Boolean] =
      state.surveyAnswers.signal.map(_.isComplete)

    /** Reset to start a new survey. */
    def reset(): Unit =
      state.currentPage.set(Page.Welcome)
      state.contactDetails.set(ContactDetails.empty)
      state.surveyAnswers.set(SurveyAnswers.empty)
      state.submissionResult.set(None)
      state.submissionError.set(None)
      state.isSubmitting.set(false)

