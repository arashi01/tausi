/*
 * Copyright (c) 2025 Tausi contributors.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package tausi.api

import scala.scalajs.js

import tausi.api.codec.Decoder
import tausi.api.codec.Encoder

/** Opaque type for command identifiers.
  *
  * Command IDs are strings in the format "command_name" or "plugin:plugin_name|command_name". They
  * are validated at construction to ensure they match the expected format.
  *
  * Examples:
  *   - Simple: "get_app_version"
  *   - Plugin: "plugin:window|set_title"
  */
opaque type CommandId = String

object CommandId:
  /** Create a CommandId from a string with validation.
    *
    * Validates that the command ID is non-empty and matches the expected format. Plugin commands
    * must follow the pattern "plugin:plugin_name|command_name".
    *
    * @param value The command ID string
    * @return Either an error message or a valid CommandId
    */
  def fromString(value: String): Either[String, CommandId] =
    if value.isEmpty then Left("Command ID cannot be empty")
    else if value.startsWith("plugin:") && !value.contains("|") then Left(s"Plugin command ID must contain '|': $value")
    else Right(value)

  /** Unsafely create a CommandId from a string without validation.
    *
    * @note Only use this when the command ID is known to be valid (e.g., compile-time constants in
    *   generated code).
    *
    * @param value The command ID string
    * @return A CommandId (unsafe - no validation)
    */
  inline def unsafe(value: String): CommandId = value

  given CanEqual[CommandId, CommandId] = CanEqual.derived

  extension (id: CommandId)
    /** Convert CommandId back to String for IPC. */
    def value: String = id

    /** Show CommandId for debugging. */
    inline def show: String = s"CommandId($id)"
end CommandId

/** Base trait for all Tauri commands with request parameters.
  *
  * Commands define type-safe interfaces to Tauri backend functions. Each command specifies:
  *   - Req: The request parameter type (must have an Encoder)
  *   - Res: The response type (must have a Decoder)
  *
  * Commands are typically defined as given instances in plugin-specific objects.
  *
  * @tparam Req The request parameter type
  * @tparam Res The response type
  *
  * @example
  *   {{{
  * // Define a command with parameters
  * final case class SetTitle(label: String, title: String)
  *
  * object SetTitle:
  *   given Codec[SetTitle] = Codec.derived
  *   given CanEqual[SetTitle, SetTitle] = CanEqual.derived
  *
  * given setTitle: Command[SetTitle, Unit] =
  *   new Command[SetTitle, Unit]:
  *     val id = CommandId.unsafe("plugin:window|set_title")
  *     given Encoder[SetTitle] = summon[Codec[SetTitle]]
  *     given Decoder[Unit] = summon[Codec[Unit]]
  *
  * // Use the command
  * invoke(SetTitle("main", "My App"))
  *   }}}
  */
trait Command[Req, Res]:
  /** The command identifier sent to the Tauri backend. */
  def id: CommandId

  /** Encoder for the request parameters. */
  given encoder: Encoder[Req]

  /** Decoder for the response. */
  given decoder: Decoder[Res]

/** Command with no request parameters (zero-argument command).
  *
  * Many Tauri commands take no parameters (e.g., app.version, window.getCurrentWindow). These use
  * Command0 which only specifies the response type.
  *
  * @tparam Res The response type
  *
  * @example
  *   {{{
  * // Define a zero-argument command
  * given version: Command0[String] =
  *   new Command0[String]:
  *     val id = CommandId.unsafe("plugin:app|version")
  *     given Decoder[String] = summon[Codec[String]]
  *
  * // Use the command with implicit resolution
  * val versionFuture: Future[String] = invoke  // Command0[String] resolved from context
  *   }}}
  */
trait Command0[Res]:
  /** The command identifier sent to the Tauri backend. */
  def id: CommandId

  /** Decoder for the response. */
  given decoder: Decoder[Res]

object Command:
  /** Summon a Command instance from implicit scope. */
  inline def apply[Req, Res](using cmd: Command[Req, Res]): Command[Req, Res] = cmd

  /** Summon a Command0 instance from implicit scope. */
  inline def apply[Res](using cmd: Command0[Res]): Command0[Res] = cmd

  /** Helper to encode request parameters to JS.Any for IPC.
    *
    * Internal use only - converts request parameters to the format expected by Tauri's IPC layer.
    */
  private[api] def encodeRequest[Req](req: Req)(using enc: Encoder[Req]): js.Any =
    enc.encode(req)

  /** Helper to decode response from JS.Any.
    *
    * Internal use only - converts IPC response to the expected Scala type.
    */
  private[api] def decodeResponse[Res](value: js.Any)(using dec: Decoder[Res]): Either[String, Res] =
    dec.decode(value)
end Command
