/*
 * Copyright (c) 2025 Tausi contributors.
 * See LICENSE file for terms.
 */
package tausi.sample.state

import com.raquo.laminar.api.L.*

import tausi.sample.model.*

/** Global application state container.
  *
  * Uses Laminar's Var for reactive state management. This is an instance-based class to allow for
  * easier testing and multiple instances if needed.
  */
final case class AppState(
    currentPage: Var[Page],
    contactDetails: Var[ContactDetails],
    surveyAnswers: Var[Map[String, String]]
):
  /** Reset all state to initial values. */
  def reset(): Unit =
    currentPage.set(Page.Welcome)
    contactDetails.set(ContactDetails.empty)
    surveyAnswers.set(Map.empty)

  /** Navigate to a specific page. */
  def navigateTo(page: Page): Unit =
    currentPage.set(page)

  /** Navigate to the next page. */
  def navigateNext(): Unit =
    Page.next(currentPage.now()).foreach(navigateTo)

  /** Navigate to the previous page. */
  def navigatePrevious(): Unit =
    Page.previous(currentPage.now()).foreach(navigateTo)

  /** Update contact details. */
  def updateContactDetails(f: ContactDetails => ContactDetails): Unit =
    contactDetails.update(f)

  /** Update a survey answer. */
  def setAnswer(questionId: String, answer: String): Unit =
    surveyAnswers.update(_ + (questionId -> answer))

  /** Clear a survey answer (for cascading field resets). */
  def clearAnswer(questionId: String): Unit =
    surveyAnswers.update(_ - questionId)

  /** Get an answer by question ID. */
  def getAnswer(questionId: String): Option[String] =
    surveyAnswers.now().get(questionId)
end AppState

object AppState:
  /** Create a new AppState with initial values. */
  def initial: AppState = new AppState(
    currentPage = Var(Page.Welcome),
    contactDetails = Var(ContactDetails.empty),
    surveyAnswers = Var(Map.empty)
  )
end AppState
