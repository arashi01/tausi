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
