/*
 * Copyright (c) 2025 Tausi contributors.
 * See LICENSE file for terms.
 */
package tausi.sample.commands

import tausi.api.Command
import tausi.api.codec.*
import tausi.sample.model.SurveySubmission

/** Survey-related Tauri commands. */
object survey:
  /** Request to save a survey submission. */
  final case class SaveSurveyRequest(submission: SurveySubmission) derives Codec

  /** Command to save survey to file.
    *
    * Uses Command.define factory for concise definition.
    */
  given saveSurvey: Command[SaveSurveyRequest, Unit] =
    Command.define[SaveSurveyRequest, Unit]("save_survey")
end survey
