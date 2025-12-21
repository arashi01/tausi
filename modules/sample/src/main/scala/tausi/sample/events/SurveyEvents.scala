/*
 * Copyright (c) 2025 Tausi contributors.
 * See LICENSE file for terms.
 */
package tausi.sample.events

import tausi.api.Event
import tausi.sample.model.*

/** Custom Tauri events for the survey application.
  *
  * Demonstrates the recommended pattern for defining application-specific
  * events using Event.define.
  *
  * @example
  *   {{{
  * import tausi.sample.events.SurveyEvents.{given, *}
  * import tausi.zio.*
  *
  * // Listen for progress updates
  * events.listen[SurveyProgressEvent] {
  *   case Right(msg) => updateProgress(msg.payload)
  *   case Left(err)  => logError(err.message)
  * }
  *
  * // Emit progress
  * events.emit(SurveyProgressEvent(2, 5, "Contact Info"))
  *   }}}
  */
object SurveyEvents:

  /** Event emitted when survey progress changes. */
  given surveyProgress: Event[SurveyProgressEvent] =
    Event.define("survey-progress")

  /** Event emitted when a survey is submitted. */
  given surveySubmitted: Event[SurveySubmittedEvent] =
    Event.define("survey-submitted")
