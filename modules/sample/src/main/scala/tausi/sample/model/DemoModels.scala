/*
 * Copyright (c) 2025 Tausi contributors.
 * See LICENSE file for terms.
 */
package tausi.sample.model

import tausi.api.codec.Codec

/** Survey application models demonstrating Tausi's Codec derivation.
  *
  * All models use `derives Codec` for automatic JSON serialisation,
  * enabling type-safe IPC with the Tauri backend.
  */

// =============================================================================
// Opaque Type Examples (demonstrating Codec.imap/iemap patterns)
// =============================================================================

/** Type-safe email address with validation.
  *
  * Demonstrates `Codec.iemap` for opaque types with decode-time validation.
  */
opaque type Email = String

object Email:
  /** Smart constructor with validation. */
  def apply(value: String): Either[String, Email] =
    if value.contains("@") && value.length >= 3 then Right(value)
    else Left(s"Invalid email format: $value")

  /** Unsafe constructor for known-valid emails. */
  def unsafe(value: String): Email = value

  extension (email: Email)
    /** Get the raw email string. */
    def value: String = email

  given CanEqual[Email, Email] = CanEqual.derived

  /** Codec using iemap for validation during decode. */
  given Codec[Email] = Codec[String].iemap(Email.apply)(_.value)
end Email

/** Type-safe survey identifier.
  *
  * Demonstrates `Codec.imap` for simple opaque type wrapping.
  */
opaque type SurveyId = String

object SurveyId:
  def apply(id: String): SurveyId = id

  extension (id: SurveyId)
    def value: String = id

  given CanEqual[SurveyId, SurveyId] = CanEqual.derived

  /** Codec using imap for simple bidirectional transformation. */
  given Codec[SurveyId] = Codec[String].imap(SurveyId.apply)(_.value)
end SurveyId

// =============================================================================
// Core Domain Models
// =============================================================================

/** Contact details for survey respondent. */
final case class ContactDetails(
    firstName: String,
    lastName: String,
    email: String,
    company: String
) derives Codec

object ContactDetails:
  given CanEqual[ContactDetails, ContactDetails] = CanEqual.derived

  def empty: ContactDetails = ContactDetails("", "", "", "")

  extension (c: ContactDetails)
    def isComplete: Boolean =
      c.firstName.nonEmpty && c.lastName.nonEmpty && c.email.nonEmpty

/** Survey answers container. */
final case class SurveyAnswers(
    satisfaction: Option[Int],
    recommendation: Option[Int],
    features: List[String],
    feedback: String
) derives Codec

object SurveyAnswers:
  given CanEqual[SurveyAnswers, SurveyAnswers] = CanEqual.derived

  def empty: SurveyAnswers = SurveyAnswers(None, None, List.empty, "")

  extension (a: SurveyAnswers)
    def isComplete: Boolean =
      a.satisfaction.isDefined && a.recommendation.isDefined

/** Complete survey submission sent to the Tauri backend. */
final case class SurveySubmission(
    contactDetails: ContactDetails,
    answers: SurveyAnswers,
    submittedAt: String
) derives Codec

object SurveySubmission:
  given CanEqual[SurveySubmission, SurveySubmission] = CanEqual.derived

// =============================================================================
// Command Request/Response Types
// =============================================================================

/** Request wrapper for saving a survey via Tauri command.
  *
  * The field name `submission` must match the Rust function parameter name
  * so that Tauri's IPC layer can correctly deserialize the request.
  */
final case class SaveSurveyRequest(submission: SurveySubmission) derives Codec

object SaveSurveyRequest:
  given CanEqual[SaveSurveyRequest, SaveSurveyRequest] = CanEqual.derived

/** Response from successful survey save. */
final case class SaveSurveyResponse(filePath: String, message: String) derives Codec

object SaveSurveyResponse:
  given CanEqual[SaveSurveyResponse, SaveSurveyResponse] = CanEqual.derived

// =============================================================================
// Event Payload Types
// =============================================================================

/** Event payload for survey progress updates. */
final case class SurveyProgressEvent(
    currentStep: Int,
    totalSteps: Int,
    stepName: String
) derives Codec

object SurveyProgressEvent:
  given CanEqual[SurveyProgressEvent, SurveyProgressEvent] = CanEqual.derived

/** Event payload for survey submission result. */
final case class SurveySubmittedEvent(
    success: Boolean,
    filePath: Option[String],
    errorMessage: Option[String]
) derives Codec

object SurveySubmittedEvent:
  given CanEqual[SurveySubmittedEvent, SurveySubmittedEvent] = CanEqual.derived
