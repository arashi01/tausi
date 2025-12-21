/*
 * Copyright (c) 2025 Tausi contributors.
 * See LICENSE file for terms.
 */
package tausi.sample.commands

import tausi.api.Command
import tausi.sample.model.*

/** Custom Tauri commands for the survey application.
  *
  * Demonstrates the recommended pattern for defining application-specific
  * commands using Command.define.
  *
  * @example
  *   {{{
  * import tausi.sample.commands.survey.{given, *}
  * import tausi.zio.*
  *
  * invoke(SaveSurveyRequest(submission)).runWith(
  *   onSuccess = response => println(s"Saved to: ${response.filePath}"),
  *   onError = err => println(s"Error: ${err.message}")
  * )
  *   }}}
  */
object survey:

  /** Command to save a completed survey to file storage.
    *
    * This command invokes the Rust backend's `save_survey` function,
    * which persists the survey data to the app's data directory.
    *
    * The `SaveSurveyRequest` wrapper has a field named `submission` which
    * matches the Rust function parameter, enabling Tauri's IPC layer to
    * correctly deserialize the request.
    */
  given saveSurvey: Command[SaveSurveyRequest, SaveSurveyResponse] =
    Command.define[SaveSurveyRequest, SaveSurveyResponse]("save_survey")
