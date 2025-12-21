/*
 * Copyright (c) 2025 Tausi contributors.
 * See LICENSE file for terms.
 */
package tausi.sample.model

/** Navigation pages for the customer survey wizard.
  *
  * The survey follows a linear wizard flow with progress tracking.
  */
enum Page(val stepNumber: Int):
  case Welcome extends Page(1)
  case ContactInfo extends Page(2)
  case SurveyQuestions extends Page(3)
  case Review extends Page(4)
  case Complete extends Page(5)

object Page:
  given CanEqual[Page, Page] = CanEqual.derived

  /** Total number of wizard steps. */
  val totalSteps: Int = 5

  /** All pages in wizard order. */
  val all: List[Page] = List(Welcome, ContactInfo, SurveyQuestions, Review, Complete)

  extension (p: Page)
    /** Display title for the page. */
    def title: String = p match
      case Welcome         => "Welcome"
      case ContactInfo     => "Contact Info"
      case SurveyQuestions => "Survey"
      case Review          => "Review"
      case Complete        => "Complete"

    /** Next page in the wizard, if any. */
    def next: Option[Page] = p match
      case Welcome         => Some(ContactInfo)
      case ContactInfo     => Some(SurveyQuestions)
      case SurveyQuestions => Some(Review)
      case Review          => Some(Complete)
      case Complete        => None

    /** Previous page in the wizard, if any. */
    def previous: Option[Page] = p match
      case Welcome         => None
      case ContactInfo     => Some(Welcome)
      case SurveyQuestions => Some(ContactInfo)
      case Review          => Some(SurveyQuestions)
      case Complete        => None

    /** Whether this is the first page. */
    def isFirst: Boolean = p == Welcome

    /** Whether this is the last page. */
    def isLast: Boolean = p == Complete

