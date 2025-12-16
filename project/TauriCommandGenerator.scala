import sbt.*
import java.time.Instant
import RustCommandParser.*

/** Generates Scala command definitions from parsed Rust commands.
  *
  * This generator creates:
  *   - Command trait instances (given instances)
  *   - Request case classes for commands with parameters
  *   - Proper module structure matching Tauri plugins
  *
  * Generated code follows the action-oriented naming strategy:
  *   - Parameter types: `SetTitle`, `ReadFile` (no "Args" suffix)
  *   - Command instances: `setTitle`, `readFile` (camelCase)
  *   - Response types: Descriptive nouns (`WindowInfo`, `FileContent`)
  */
object TauriCommandGenerator {

  /** Group commands by plugin for module organization */
  private def groupByPlugin(commands: List[RustCommand]): Map[String, List[RustCommand]] =
    commands.groupBy(_.plugin)

  /** Generate all command files for the given commands
    *
    * @param commands Parsed Rust commands
    * @param outputDir Target directory for generated files
    * @param tauriGitSha Git SHA of the Tauri source used for generation
    * @param log SBT logger
    * @return Map of generated files to their content
    */
  def generate(commands: List[RustCommand], outputDir: File, tauriGitSha: String, log: Logger): Map[File, String] = {
    val commandsByPlugin = groupByPlugin(commands)

    log.info(s"Generating ${commands.size} commands across ${commandsByPlugin.size} plugins")

    commandsByPlugin.flatMap { case (pluginName, pluginCommands) =>
      log.info(s"  Plugin $pluginName: ${pluginCommands.size} commands")
      generatePluginModule(pluginName, pluginCommands, outputDir, tauriGitSha)
    }
  }

  /** Generate a single plugin module file
    *
    * @param pluginName Name of the plugin (e.g., "app", "window")
    * @param commands Commands for this plugin
    * @param outputDir Base output directory
    * @param tauriGitSha Git SHA of Tauri source
    * @return Map with single file entry
    */
  private def generatePluginModule(
    pluginName: String,
    commands: List[RustCommand],
    outputDir: File,
    tauriGitSha: String
  ): Map[File, String] =
    // Note: All exclusions should be configured via tauriCommandExclusions setting in build.sbt
    // No automatic filtering here - developers must explicitly list excluded commands

    // Skip generating file if no commands remain
    if (commands.isEmpty) {
      System.err.println(s"[WARN] Plugin '$pluginName' has no commands (all excluded), skipping file generation")
      Map.empty
    } else {
      val targetFile = outputDir / "tausi" / "api" / "commands" / s"$pluginName.scala"
      val content = generatePluginContent(pluginName, commands, tauriGitSha)
      Map(targetFile -> content)
    }

  /** Generate the Scala source code for a plugin module */
  private def generatePluginContent(pluginName: String, commands: List[RustCommand], tauriGitSha: String): String = {
    val header = generateHeader(pluginName, commands, tauriGitSha)
    val requestTypes = generateRequestTypes(commands)
    val commandInstances = generateCommandInstances(commands)

    s"""$header
       |
       |$requestTypes
       |
       |$commandInstances
       |""".stripMargin
  }

  /** Generate file header with package and imports */
  private def generateHeader(pluginName: String, commands: List[RustCommand], tauriGitSha: String): String = {
    // Determine what to import based on what's actually used
    // Note: commands here are already filtered to exclude js.Dynamic params
    val hasZeroArgCmd = commands.exists { cmd =>
      val userParams = cmd.params.filterNot(_.isFrameworkType)
      userParams.isEmpty
    }
    val hasParameterizedCmd = commands.exists { cmd =>
      val userParams = cmd.params.filterNot(_.isFrameworkType)
      userParams.nonEmpty
    }
    val usesResourceId = commands.exists(cmd =>
      cmd.params.exists(p => RustCommandParser.mapRustType(p.rustType) == "ResourceId") ||
        RustCommandParser.mapRustType(cmd.returnType) == "ResourceId"
    )
    val usesEventName = commands.exists(cmd =>
      cmd.params.exists(p => RustCommandParser.mapRustType(p.rustType) == "EventName") ||
        RustCommandParser.mapRustType(cmd.returnType) == "EventName"
    )
    val usesEventTarget = commands.exists(cmd =>
      cmd.params.exists(p => RustCommandParser.mapRustType(p.rustType) == "EventTarget") ||
        RustCommandParser.mapRustType(cmd.returnType) == "EventTarget"
    )
    val usesEventId = commands.exists(cmd =>
      cmd.params.exists(p => RustCommandParser.mapRustType(p.rustType) == "EventId") ||
        RustCommandParser.mapRustType(cmd.returnType) == "EventId"
    )

    val commandImports = List(
      if (hasParameterizedCmd) Some("Command") else None,
      if (hasZeroArgCmd) Some("Command0") else None,
      Some("CommandId"),
      // Note: InvokeArgs is not directly used, it's an internal type of Command
      if (usesResourceId) Some("ResourceId") else None,
      if (usesEventName) Some("EventName") else None,
      if (usesEventTarget) Some("EventTarget") else None,
      if (usesEventId) Some("EventId") else None
    ).flatten

    val givenImports = List(
      if (usesResourceId) Some("import tausi.api.ResourceId.given") else None,
      if (usesEventName) Some("import tausi.api.EventName.given") else None,
      if (usesEventTarget) Some("import tausi.api.EventTarget.given") else None
    ).flatten
    val resourceIdGiven = if (givenImports.nonEmpty) "\n" + givenImports.mkString("\n") else ""

    s"""// DO NOT EDIT - Generated by TauriCommandGenerator at ${Instant.now}
       |// Source: Tauri (https://github.com/tauri-apps/tauri) at commit ${tauriGitSha}
       |package tausi.api.commands
       |
       |import tausi.api.{${commandImports.mkString(", ")}}$resourceIdGiven
       |import tausi.api.codec.{Encoder, Decoder, Codec}
       |import scala.scalajs.js
       |
       |/** Commands for the `$pluginName` Tauri plugin.
       |  *
       |  * This object provides type-safe command instances for all operations
       |  * in the $pluginName plugin. Each command is a `given` instance that can
       |  * be imported and used with the `invoke` methods.
       |  *
       |  * This trait is package-private and serves as the base for the public API.
       |  * Manual command implementations can be added by extending this trait in a
       |  * public object with the same name.
       |  *
       |  * @example {{{
       |  * import tausi.api.commands.$pluginName.{given, *}
       |  * import tausi.api.core.invoke
       |  *
       |  * // Zero-argument command
       |  * val result = invoke(using CommandInstance)
       |  *
       |  * // Parameterized command
       |  * val result = invoke(RequestType(param1, param2))
       |  * }}}
       |  */
       |private[commands] transparent trait ${pluginName.capitalize}CommandsGenerated:""".stripMargin
  }

  /** Generate request type case classes for commands with parameters */
  private def generateRequestTypes(commands: List[RustCommand]): String = {
    val typeDefs = commands
      .filter { cmd =>
        val userParams = cmd.params.filterNot(_.isFrameworkType)
        userParams.nonEmpty
      }
      .map(generateRequestType)
      .filter(_.nonEmpty)
      .mkString("\n\n")

    if (typeDefs.isEmpty) "" else s"  // Request Types\n\n$typeDefs"
  }

  /** Generate a single request type case class */
  private def generateRequestType(cmd: RustCommand): String = {
    val userParams = cmd.params.filterNot(_.isFrameworkType)

    if (userParams.isEmpty) {
      "" // No request type needed for zero-arg commands
    } else {
      val params = userParams
        .map { p =>
          val scalaType = mapRustType(p.rustType)
          s"${p.name}: $scalaType"
        }
        .mkString(", ")

      val docComment = generateRequestTypeDoc(cmd, userParams)

      s"""$docComment
         |  final case class ${cmd.actionName}($params) derives CanEqual, Codec""".stripMargin
    }
  }

  /** Generate ScalaDoc for request type */
  private def generateRequestTypeDoc(cmd: RustCommand, params: List[RustParam]): String = {
    val paramDocs = params
      .map { p =>
        s"    * @param ${p.name} ${formatTypeDoc(p.rustType)}"
      }
      .mkString("\n")

    s"""  /** Request parameters for the `${cmd.rustName}` command.
       |    *
       |$paramDocs
       |    */""".stripMargin
  }

  /** Format type documentation */
  private def formatTypeDoc(rustType: String): String =
    rustType match {
      case "String" | "str"             => "String value"
      case "bool"                       => "Boolean flag"
      case t if t.startsWith("Option<") => s"Optional ${extractGeneric(t, "Option")}"
      case t if t.startsWith("Vec<")    => s"Array of ${extractGeneric(t, "Vec")}"
      case _                            => rustType
    }

  /** Generate command instances (given values) */
  private def generateCommandInstances(commands: List[RustCommand]): String = {
    // Count occurrences of each name upfront
    val nameCounts = commands.groupBy(_.scalaName).map { case (name, cmds) => name -> cmds.size }
    val nameIndices = scala.collection.mutable.Map[String, Int]()

    val instances = commands
      .map { cmd =>
        val baseName = cmd.scalaName
        val totalCount = nameCounts(baseName)
        val currentIndex = nameIndices.getOrElse(baseName, 0)
        nameIndices(baseName) = currentIndex + 1

        // If there are multiple commands with same name, add targetName annotation to all but the first
        val needsTargetName = totalCount > 1 && currentIndex > 0
        generateCommandInstance(cmd, needsTargetName, currentIndex)
      }
      .mkString("\n\n")

    s"  // Command Instances\n\n$instances"
  }

  /** Generate a single command instance */
  private def generateCommandInstance(cmd: RustCommand, needsTargetName: Boolean, suffix: Int): String = {
    val userParams = cmd.params.filterNot(_.isFrameworkType)
    val returnType = mapRustType(cmd.returnType)

    if (userParams.isEmpty) {
      generateZeroArgCommand(cmd, returnType, needsTargetName, suffix)
    } else {
      generateParameterizedCommand(cmd, returnType, needsTargetName, suffix)
    }
  }

  /** Generate a zero-argument command (Command0) */
  private def generateZeroArgCommand(cmd: RustCommand, returnType: String, needsTargetName: Boolean, suffix: Int): String = {
    val targetNameAnnotation = if (needsTargetName) s"""  @scala.annotation.targetName("${cmd.scalaName}_$suffix")
                                                       |""".stripMargin else ""
    val docComment = generateCommandDoc(cmd, returnType, isZeroArg = true)
    val decoderImpl = if (returnType == "js.Dynamic" || returnType == "js.Any") {
      s"new Decoder[$returnType] { def decode(value: js.Any): Either[String, $returnType] = Right(value.asInstanceOf[$returnType]) }"
    } else {
      s"summon[Codec[$returnType]]"
    }

    s"""$targetNameAnnotation$docComment
       |  given ${cmd.scalaName}: Command0[$returnType] = new Command0[$returnType]:
       |    val id = CommandId.unsafe("${cmd.commandId}")
       |    given decoder: Decoder[$returnType] = $decoderImpl""".stripMargin
  }

  /** Generate a parameterized command (Command) */
  private def generateParameterizedCommand(cmd: RustCommand, returnType: String, needsTargetName: Boolean, suffix: Int): String = {
    val targetNameAnnotation = if (needsTargetName) s"""  @scala.annotation.targetName("${cmd.scalaName}_$suffix")
                                                       |""".stripMargin else ""
    val docComment = generateCommandDoc(cmd, returnType, isZeroArg = false)
    val decoderImpl = if (returnType == "js.Dynamic" || returnType == "js.Any") {
      s"new Decoder[$returnType] { def decode(value: js.Any): Either[String, $returnType] = Right(value.asInstanceOf[$returnType]) }"
    } else {
      s"summon[Codec[$returnType]]"
    }

    s"""$targetNameAnnotation$docComment
       |  given ${cmd.scalaName}: Command[${cmd.actionName}, $returnType] = new Command[${cmd.actionName}, $returnType]:
       |    val id = CommandId.unsafe("${cmd.commandId}")
       |    given encoder: Encoder[${cmd.actionName}] = summon[Codec[${cmd.actionName}]]
       |    given decoder: Decoder[$returnType] = $decoderImpl""".stripMargin
  }

  /** Generate ScalaDoc for command instance */
  private def generateCommandDoc(cmd: RustCommand, returnType: String, isZeroArg: Boolean): String = {
    val usage = if (isZeroArg) {
      s"invoke(using ${cmd.scalaName})"
    } else {
      s"invoke(${cmd.actionName}(...))"
    }

    val platformNote = if (cmd.platformConditions.nonEmpty) {
      s"\n    * @note Platform-specific: ${cmd.platformConditions.mkString(", ")}"
    } else ""

    s"""  /** Command instance for `${cmd.rustName}`.
       |    *
       |    * @return $returnType$platformNote
       |    * @example {{{
       |    * val result = $usage
       |    * }}}
       |    */""".stripMargin
  }

  /** Extract generic type parameter (reuse from parser) */
  private def extractGeneric(typeStr: String, wrapper: String): String = {
    val start = typeStr.indexOf('<')
    val end = typeStr.lastIndexOf('>')
    if (start > 0 && end > start) {
      typeStr.substring(start + 1, end).trim
    } else {
      "js.Any"
    }
  }
}
