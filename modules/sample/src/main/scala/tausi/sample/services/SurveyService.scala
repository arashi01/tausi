/*
 * Copyright (c) 2025 Tausi contributors.
 * See LICENSE file for terms.
 */
package tausi.sample.services

import zio.*

import tausi.api.TauriError
import tausi.sample.commands.survey.{given, *}
import tausi.sample.model.SurveySubmission

/** Service for survey-related operations using ZIO.
  *
  * Uses ZIO's IO type with TauriError in the error channel for proper typed error handling.
  */
trait SurveyService:
  /** Submit a survey to be saved to a file.
    *
    * @param submission The survey submission data
    * @return IO effect that may fail with TauriError
    */
  def submitSurvey(submission: SurveySubmission): IO[TauriError, Unit]
end SurveyService

object SurveyService:
  /** Create a live implementation that uses Tauri commands via ZIO. */
  def live: SurveyService = LiveSurveyService

  /** Create a mock implementation for testing. */
  def mock: SurveyService = MockSurveyService

  // ============================================================
  // API DEFICIENCY: Callback/UI Integration Utilities
  // ============================================================
  // The Tausi API should provide utilities for running ZIO effects
  // with callbacks, specifically for UI framework integration.
  //
  // Proposed API addition to tausi.zio package:
  //
  //   def runWithCallbacks[E, A](
  //     effect: IO[E, A],
  //     onSuccess: A => Unit,
  //     onError: E => Unit
  //   ): Unit
  //
  //   def runToVar[E, A](
  //     effect: IO[E, A],
  //     resultVar: Var[Option[Either[E, A]]]
  //   ): Unit
  //
  //   // Or even better, Laminar-specific integration:
  //   extension [E, A](effect: IO[E, A])
  //     def toEventStream: EventStream[Either[E, A]]
  //     def toSignal(initial: A): Signal[Either[E, A]]
  //
  // This would eliminate boilerplate for the common pattern of:
  // - Running a ZIO effect from a UI callback
  // - Updating Laminar Vars based on success/failure
  // ============================================================

  /** Run a ZIO effect with callbacks for UI integration.
    *
    * TEMPORARY UTILITY - This pattern should be provided by tausi.zio.
    *
    * @param effect The ZIO effect to run
    * @param onSuccess Callback invoked on success with the result
    * @param onError Callback invoked on error with the TauriError
    */
  def runEffect[A](
      effect: IO[TauriError, A],
      onSuccess: A => Unit,
      onError: TauriError => Unit
  ): Unit =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe
        .runToFuture(effect)
        .future
        .onComplete {
          case scala.util.Success(a) => onSuccess(a)
          case scala.util.Failure(ex) => onError(TauriError.fromThrowable(ex))
        }(using scala.concurrent.ExecutionContext.global)
    }
end SurveyService

/** Live implementation using Tauri IPC via ZIO. */
private object LiveSurveyService extends SurveyService:
  override def submitSurvey(submission: SurveySubmission): IO[TauriError, Unit] =
    tausi.zio.invoke(SaveSurveyRequest(submission))
end LiveSurveyService

/** Mock implementation for development/testing. */
private object MockSurveyService extends SurveyService:
  override def submitSurvey(submission: SurveySubmission): IO[TauriError, Unit] =
    ZIO.succeed {
      org.scalajs.dom.console.log("Survey Submission (mock):")
      org.scalajs.dom.console.log(formatSubmission(submission))
    }.delay(500.millis)

  private def formatSubmission(submission: SurveySubmission): String =
    val contact = submission.contactDetails
    val answers = submission.answers.map { case (k, v) => s"  $k: $v" }.mkString("\n")

    s"""=== Survey Submission ===
       |Submitted at: ${submission.submittedAt}
       |Contact: ${contact.firstName} ${contact.lastName}
       |Phone: ${contact.phoneNumber}
       |Answers:
       |$answers""".stripMargin
end MockSurveyService
