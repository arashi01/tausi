import sbt.*
import scala.io.Source
import scala.util.Using
import scala.util.matching.Regex

/** Parses Tauri Rust plugin source files to extract command definitions.
  *
  * This parser extracts information from Rust `#[command]` macros and their associated function
  * signatures, including:
  *   - Command names (converted to plugin:name format)
  *   - Parameter types and names
  *   - Return types
  *   - Platform-specific conditional compilation flags
  */
object RustCommandParser {

  /** Represents a parsed Rust command from Tauri source code */
  final case class RustCommand(
    /** Original Rust function name (snake_case) */
    rustName: String,
    /** Scala-style name (camelCase) */
    scalaName: String,
    /** Capitalized action name for parameter type */
    actionName: String,
    /** Plugin name (extracted from file path) */
    plugin: String,
    /** Full command ID in plugin:name format */
    commandId: String,
    /** Function parameters (excluding framework types like AppHandle, Webview, State) */
    params: List[RustParam],
    /** Return type (inside Result<T>) */
    returnType: String,
    /** Whether this is an async command */
    isAsync: Boolean,
    /** Platform-specific compilation conditions */
    platformConditions: List[String]
  )

  /** Represents a parsed Rust function parameter */
  final case class RustParam(
    /** Parameter name */
    name: String,
    /** Rust type */
    rustType: String,
    /** Whether this is a framework type (should be excluded from Command params) */
    isFrameworkType: Boolean
  )

  /** Parse all Rust command definitions from Tauri plugin source files
    *
    * @param tauriSourceDir Root directory of Tauri Rust source (e.g.,
    *   external-references/tauri/crates/tauri/src)
    * @param log SBT logger for output
    * @return List of parsed commands
    */
  def parseCommands(tauriSourceDir: File, log: Logger): List[RustCommand] =
    if (!tauriSourceDir.exists()) {
      log.warn(s"Tauri source directory not found: ${tauriSourceDir.getAbsolutePath}")
      List.empty
    } else {
      val pluginFiles = (tauriSourceDir ** "plugin.rs").get.toList
      log.info(s"Found ${pluginFiles.size} plugin.rs files")

      pluginFiles.flatMap { file =>
        val pluginName = extractPluginName(file, tauriSourceDir)
        log.debug(s"Parsing plugin: $pluginName from ${file.getName}")
        parsePluginFile(file, pluginName, log)
      }
    }

  /** Extract plugin name from file path e.g., .../tauri/src/app/plugin.rs -> "app" e.g.,
    * .../tauri/src/plugin.rs -> "core"
    */
  private def extractPluginName(pluginFile: File, rootDir: File): String = {
    val relativePath = IO.relativize(rootDir, pluginFile).getOrElse(pluginFile.getName)
    val parts = relativePath.split('/').toList
    if (parts.size > 1) parts(parts.size - 2) // Parent directory name
    else "core"
  }

  /** Parse a single plugin.rs file to extract all commands */
  private def parsePluginFile(file: File, pluginName: String, log: Logger): List[RustCommand] =
    Using.resource(Source.fromFile(file, "UTF-8")) { source =>
      val content = source.mkString
      extractCommands(content, pluginName, log)
    }

  /** Extract all command definitions from file content */
  private def extractCommands(content: String, pluginName: String, log: Logger): List[RustCommand] = {
    // Pattern to match #[command] with optional attributes, followed by function signature
    val commandPattern: Regex =
      """(?s)#\[command(?:\([^)]*\))?\]\s*(?:#\[cfg\([^)]+\)\]\s*)*(?:pub\s+)?(?:async\s+)?fn\s+(\w+)\s*<[^>]+>\s*\((.*?)\)\s*->\s*(?:crate::)?Result<([^>]+)>""".r
    val simpleCmdPattern: Regex =
      """(?s)#\[command(?:\([^)]*\))?\]\s*(?:#\[cfg\([^)]+\)\]\s*)*(?:pub\s+)?(?:async\s+)?fn\s+(\w+)\s*\((.*?)\)\s*->\s*(?:crate::)?Result<([^>]+)>""".r
    val asyncPattern = """async\s+fn\s+(\w+)""".r
    val cfgPattern = """#\[cfg\(([^)]+)\)\]""".r

    val commands = scala.collection.mutable.ListBuffer[RustCommand]()

    // Find all command declarations
    var position = 0
    while (position < content.length) {
      // Look for #[command
      val cmdIdx = content.indexOf("#[command", position)
      if (cmdIdx >= 0) {
        // Extract a chunk of code (up to next #[command or end)
        val nextCmdIdx = content.indexOf("#[command", cmdIdx + 10)
        val endIdx = if (nextCmdIdx > 0) nextCmdIdx else content.length
        val chunk = content.substring(cmdIdx, endIdx)

        // Try to match the command pattern
        val matchOpt = commandPattern.findFirstMatchIn(chunk).orElse(simpleCmdPattern.findFirstMatchIn(chunk))

        matchOpt.foreach { m =>
          val functionName = m.group(1)
          val paramsStr = m.group(2)
          val returnType = m.group(3).trim

          // Check if async
          val isAsync = asyncPattern.findFirstMatchIn(chunk).exists(_.group(1) == functionName)

          // Extract platform conditions
          val platformConditions = cfgPattern.findAllMatchIn(chunk).map(_.group(1)).toList

          // Parse parameters
          val params = parseParameters(paramsStr)

          // Convert names
          val scalaName = toScalaName(functionName)
          val actionName = toActionName(functionName)
          val commandId = s"plugin:$pluginName|$functionName"

          commands += RustCommand(
            rustName = functionName,
            scalaName = scalaName,
            actionName = actionName,
            plugin = pluginName,
            commandId = commandId,
            params = params,
            returnType = returnType,
            isAsync = isAsync,
            platformConditions = platformConditions
          )

          log.debug(s"  Found command: $functionName -> $commandId (${params.size} params)")
        }

        position = cmdIdx + 10
      } else {
        position = content.length
      }
    }

    commands.toList
  }

  /** Parse function parameters from parameter list string */
  private def parseParameters(paramsStr: String): List[RustParam] =
    if (paramsStr.trim.isEmpty) {
      List.empty
    } else {
      // Split by comma, but respect nested generics and parentheses
      val params = smartSplit(paramsStr, ',')

      params.flatMap { paramStr =>
        val trimmed = paramStr.trim
        if (trimmed.isEmpty) {
          None
        } else {
          // Extract parameter name and type
          // Format: "name: Type" or "_name: Type" or "mut name: Type"
          val parts = trimmed.split(':').map(_.trim)
          if (parts.length >= 2) {
            val name = parts(0).split("\\s+").last.stripPrefix("_") // Handle "mut name", "_name"
            val rustType = parts.drop(1).mkString(":").trim

            // Check if this is a framework type that should be excluded
            val isFramework = isFrameworkType(rustType)

            Some(RustParam(name, rustType, isFramework))
          } else {
            None
          }
        }
      }
    }

  /** Check if a type is a Tauri framework type (AppHandle, Webview, State, etc.) */
  private def isFrameworkType(rustType: String): Boolean = {
    val frameworkTypes = Set(
      "AppHandle",
      "Webview",
      "Window",
      "State",
      "ResourceTable",
      "Manager",
      "Runtime",
      "Channel",
      "Env"
    )
    // Check if type contains any framework type (handles generics like State<'_, T>)
    frameworkTypes.exists(ft => rustType.contains(ft))
  }

  /** Smart split that respects nested brackets and generics */
  private def smartSplit(str: String, delimiter: Char): List[String] = {
    val result = scala.collection.mutable.ListBuffer[String]()
    val current = new StringBuilder
    var depth = 0
    var angleDepth = 0

    str.foreach { ch =>
      ch match {
        case '('                                                  => depth += 1; current.append(ch)
        case ')'                                                  => depth -= 1; current.append(ch)
        case '<'                                                  => angleDepth += 1; current.append(ch)
        case '>'                                                  => angleDepth -= 1; current.append(ch)
        case c if c == delimiter && depth == 0 && angleDepth == 0 =>
          result += current.toString
          current.clear()
        case c => current.append(c)
      }
    }

    if (current.nonEmpty) {
      result += current.toString
    }

    result.toList
  }

  /** Convert snake_case Rust name to camelCase Scala name */
  private def toScalaName(rustName: String): String = {
    val parts = rustName.split('_')
    val name = (parts.head +: parts.tail.map(_.capitalize)).mkString
    escapeReservedKeyword(name)
  }

  /** Escape Scala reserved keywords */
  private def escapeReservedKeyword(name: String): String = {
    val reserved =
      Set("new", "type", "val", "var", "def", "class", "object", "trait", "extends", "with", "import", "package", "match", "case", "return")
    if (reserved.contains(name)) s"`$name`" else name
  }

  /** Convert snake_case Rust name to CapitalizedActionName */
  private def toActionName(rustName: String): String = {
    val name = rustName.split('_').map(_.capitalize).mkString
    escapeReservedKeyword(name)
  }

  /** Map Rust types to Scala.js types
    *
    * @param rustType Rust type string
    * @return Equivalent Scala type
    */
  def mapRustType(rustType: String): String = {
    // Remove whitespace and references
    val cleaned = rustType.trim.stripPrefix("&").trim

    cleaned match {
      // Primitive types
      case "String" | "str"                            => "String"
      case "()"                                        => "Unit"
      case "bool"                                      => "Boolean"
      case "i8" | "i16" | "i32" | "u8" | "u16" | "u32" => "Int"
      case "i64" | "u64" | "isize" | "usize"           => "Long"
      case "f32" | "f64"                               => "Double"

      // Option types
      case s if s.startsWith("Option<") =>
        val inner = extractGeneric(s, "Option")
        s"Option[${mapRustType(inner)}]"

      // Vec types
      case s if s.startsWith("Vec<") =>
        val inner = extractGeneric(s, "Vec")
        s"js.Array[${mapRustType(inner)}]"

      // Result types (should not appear in mapped types, but handle anyway)
      case s if s.startsWith("Result<") =>
        val inner = extractGeneric(s, "Result")
        mapRustType(inner)

      // PathBuf
      case "PathBuf" | "Path" => "String"

      // Common Tauri types
      case "ResourceId"       => "ResourceId"
      case "Position"         => "js.Dynamic" // Will need proper type
      case "Size"             => "js.Dynamic"
      case "PhysicalPosition" => "js.Dynamic"
      case "PhysicalSize"     => "js.Dynamic"
      case "LogicalPosition"  => "js.Dynamic"
      case "LogicalSize"      => "js.Dynamic"

      // Event types
      case "EventName"                     => "EventName"
      case s if s.startsWith("EventName<") => "EventName" // EventName<&str> or EventName<String>
      case "EventTarget"                   => "EventTarget"
      case "EventId"                       => "EventId"

      // Serde JSON
      case "Value" | "serde_json::Value" | "JsonValue" => "js.Any"

      // Fallback to js.Dynamic for unknown types
      case _ if cleaned.contains("::")                 => "js.Dynamic" // Qualified types
      case _ if cleaned.matches("^[A-Z][a-zA-Z0-9]*$") => "js.Dynamic" // Custom types
      case _                                           => "js.Dynamic"
    }
  }

  /** Extract generic type parameter from Type<Param> */
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
