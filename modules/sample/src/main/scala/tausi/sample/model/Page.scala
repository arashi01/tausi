/*
 * Copyright (c) 2025 Tausi contributors.
 * See LICENSE file for terms.
 */
package tausi.sample.model

/** Application navigation pages. */
enum Page(val title: String):
  case Welcome extends Page("Welcome")
  case ContactInfo extends Page("Contact Info")
  case SurveyPageOne extends Page("Experience")
  case SurveyPageTwo extends Page("Feedback")
  case Submit extends Page("Submit")

  /** Get the step number (1-indexed) for progress indicator. */
  def stepNumber: Int = this match
    case Page.Welcome       => 1
    case Page.ContactInfo   => 2
    case Page.SurveyPageOne => 3
    case Page.SurveyPageTwo => 4
    case Page.Submit        => 5
end Page

object Page:
  given CanEqual[Page, Page] = CanEqual.derived

  /** Get the next page in the flow. */
  def next(current: Page): Option[Page] = current match
    case Page.Welcome       => Some(Page.ContactInfo)
    case Page.ContactInfo   => Some(Page.SurveyPageOne)
    case Page.SurveyPageOne => Some(Page.SurveyPageTwo)
    case Page.SurveyPageTwo => Some(Page.Submit)
    case Page.Submit        => None

  /** Get the previous page in the flow. */
  def previous(current: Page): Option[Page] = current match
    case Page.Welcome       => None
    case Page.ContactInfo   => Some(Page.Welcome)
    case Page.SurveyPageOne => Some(Page.ContactInfo)
    case Page.SurveyPageTwo => Some(Page.SurveyPageOne)
    case Page.Submit        => Some(Page.SurveyPageTwo)

  /** Total number of steps. */
  val totalSteps: Int = 5
end Page
