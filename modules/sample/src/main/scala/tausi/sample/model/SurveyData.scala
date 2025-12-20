/*
 * Copyright (c) 2025 Tausi contributors.
 * See LICENSE file for terms.
 */
package tausi.sample.model

import tausi.api.codec.Codec

/** Contact details collected at the start of the survey. */
final case class ContactDetails(
    firstName: String,
    lastName: String,
    phoneNumber: String
)

object ContactDetails:
  given CanEqual[ContactDetails, ContactDetails] = CanEqual.derived
  given Codec[ContactDetails] = Codec.derived

  val empty: ContactDetails = ContactDetails("", "", "")
end ContactDetails

/** A single survey question. */
final case class SurveyQuestion(
    id: String,
    text: String,
    questionType: QuestionType,
    required: Boolean,
    options: Option[List[String]]
)

object SurveyQuestion:
  given CanEqual[SurveyQuestion, SurveyQuestion] = CanEqual.derived
  given Codec[SurveyQuestion] = Codec.derived
end SurveyQuestion

/** Types of survey questions. */
enum QuestionType:
  case Rating
  case Text
  case MultiChoice

object QuestionType:
  given CanEqual[QuestionType, QuestionType] = CanEqual.derived
  given Codec[QuestionType] = Codec.derived
end QuestionType

/** A page of survey questions. */
final case class SurveyPage(
    id: String,
    title: String,
    description: String,
    questions: List[SurveyQuestion]
)

object SurveyPage:
  given CanEqual[SurveyPage, SurveyPage] = CanEqual.derived
  given Codec[SurveyPage] = Codec.derived
end SurveyPage

/** Complete survey submission. */
final case class SurveySubmission(
    contactDetails: ContactDetails,
    answers: Map[String, String],
    submittedAt: String
)

object SurveySubmission:
  given CanEqual[SurveySubmission, SurveySubmission] = CanEqual.derived
  given Codec[SurveySubmission] = Codec.derived
end SurveySubmission

/** Validation result for form fields. */
enum ValidationResult:
  case Valid
  case Invalid(message: String)

object ValidationResult:
  given CanEqual[ValidationResult, ValidationResult] = CanEqual.derived

  extension (result: ValidationResult)
    def isValid: Boolean = result match
      case Valid      => true
      case Invalid(_) => false

    def errorMessage: Option[String] = result match
      case Valid        => None
      case Invalid(msg) => Some(msg)
  end extension
end ValidationResult
