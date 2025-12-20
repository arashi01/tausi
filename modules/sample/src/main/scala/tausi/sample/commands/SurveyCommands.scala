/*
 * Copyright (c) 2025 Tausi contributors.
 * See LICENSE file for terms.
 */
package tausi.sample.commands

import tausi.api.Command
import tausi.api.CommandId
import tausi.api.codec.*
import tausi.sample.model.SurveySubmission

/** Survey-related Tauri commands. */
object survey:
  /** Request to save a survey submission. */
  final case class SaveSurveyRequest(submission: SurveySubmission)

  object SaveSurveyRequest:
    given Codec[SaveSurveyRequest] = Codec.derived

  /** Command to save survey to file. */
  given saveSurvey: Command[SaveSurveyRequest, Unit] =
    new Command[SaveSurveyRequest, Unit]:
      val id: CommandId = CommandId.unsafe("save_survey")
      given encoder: Encoder[SaveSurveyRequest] = summon[Codec[SaveSurveyRequest]]
      given decoder: Decoder[Unit] = summon[Codec[Unit]]
end survey
