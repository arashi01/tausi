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

import tausi.api.codec.Codec
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
    inline def value: String = id

    /** Show CommandId for debugging. */
    inline def show: String = s"CommandId($id)"
end CommandId

/** Base trait for all Tauri commands with request parameters.
  *
  * Commands define type-safe interfaces to Tauri backend functions. Each command specifies:
  *   - Req: The request parameter type (must have an Encoder)
  *   - Res: The response type (must have a Decoder)
  *
  * Commands are typically defined as given instances using the `Command.define` factory.
  *
  * @tparam Req The request parameter type
  * @tparam Res The response type
  *
  * @example
  *   {{{
  * import tausi.api.Command
  * import tausi.api.codec.Codec
  *
  * // Define a command with parameters using Codec derivation
  * final case class SetTitle(label: String, title: String) derives Codec, CanEqual
  *
  * // Create the command instance using the factory method
  * given setTitle: Command[SetTitle, Unit] =
  *   Command.define[SetTitle, Unit]("plugin:window|set_title")
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
  * import tausi.api.Command
  * import tausi.api.codec.Codec
  *
  * // Define a zero-argument command using the factory method
  * given version: Command0[String] = Command.define0[String]("plugin:app|version")
  *
  * // Use the command with implicit resolution (no arguments needed)
  * val versionFuture: Future[String] = invoke
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

  // ============================================================
  // Implementation Classes
  // ============================================================

  /** Concrete implementation of Command for factory methods.
    *
    * Uses @publicInBinary to allow inline factory methods to instantiate
    * while keeping the class private to the Command companion object.
    */
  final class Impl[Req, Res] @scala.annotation.publicInBinary private[Command] (
    val id: CommandId,
    enc: Encoder[Req],
    dec: Decoder[Res]
  ) extends Command[Req, Res]:
    given encoder: Encoder[Req] = enc
    given decoder: Decoder[Res] = dec

  /** Concrete implementation of Command0 for factory methods. */
  final class Impl0[Res] @scala.annotation.publicInBinary private[Command] (
    val id: CommandId,
    dec: Decoder[Res]
  ) extends Command0[Res]:
    given decoder: Decoder[Res] = dec

  // ============================================================
  // Factory Methods for User-Defined Commands
  // ============================================================

  /** Create a command with automatic codec resolution.
    *
    * This factory method simplifies defining custom commands by automatically
    * resolving Encoder and Decoder instances from the provided Codecs.
    *
    * @param commandId The command identifier string (e.g., "save_survey" or "plugin:myplugin|my_command")
    * @tparam Req The request parameter type (must have a Codec instance)
    * @tparam Res The response type (must have a Codec instance)
    * @return A Command instance ready for use with invoke
    *
    * @example
    *   {{{
    * final case class SaveSurveyRequest(submission: SurveySubmission) derives Codec
    *
    * // Simple one-liner command definition
    * given saveSurvey: Command[SaveSurveyRequest, Unit] =
    *   Command.define[SaveSurveyRequest, Unit]("save_survey")
    *
    * // Use with invoke
    * invoke(SaveSurveyRequest(submission))
    *   }}}
    */
  inline def define[Req, Res](inline commandId: String)(using
    reqCodec: Codec[Req],
    resCodec: Codec[Res]
  ): Command[Req, Res] =
    Impl[Req, Res](CommandId.unsafe(commandId), reqCodec, resCodec)

  /** Create a command with explicit encoder and decoder.
    *
    * Use this variant when you have separate Encoder and Decoder instances
    * instead of unified Codec instances.
    *
    * @param commandId The command identifier string
    * @param enc Encoder for the request type
    * @param dec Decoder for the response type
    * @tparam Req The request parameter type
    * @tparam Res The response type
    * @return A Command instance ready for use with invoke
    *
    * @example
    *   {{{
    * given myCommand: Command[MyRequest, MyResponse] =
    *   Command.defineWith[MyRequest, MyResponse]("my_command")(
    *     myRequestEncoder,
    *     myResponseDecoder
    *   )
    *   }}}
    */
  inline def defineWith[Req, Res](inline commandId: String)(
    enc: Encoder[Req],
    dec: Decoder[Res]
  ): Command[Req, Res] =
    Impl[Req, Res](CommandId.unsafe(commandId), enc, dec)

  /** Create a zero-argument command with automatic codec resolution.
    *
    * Use this for commands that take no parameters.
    *
    * @param commandId The command identifier string
    * @tparam Res The response type (must have a Codec instance)
    * @return A Command0 instance ready for use with invoke
    *
    * @example
    *   {{{
    * // Zero-argument command returning a String
    * given getVersion: Command0[String] =
    *   Command.define0[String]("get_version")
    *
    * // Use with invoke (no parameters)
    * val version: Future[String] = invoke
    *   }}}
    */
  inline def define0[Res](inline commandId: String)(using
    resCodec: Codec[Res]
  ): Command0[Res] =
    Impl0[Res](CommandId.unsafe(commandId), resCodec)

  /** Create a zero-argument command with explicit decoder.
    *
    * @param commandId The command identifier string
    * @param dec Decoder for the response type
    * @tparam Res The response type
    * @return A Command0 instance ready for use with invoke
    */
  inline def define0With[Res](inline commandId: String)(
    dec: Decoder[Res]
  ): Command0[Res] =
    Impl0[Res](CommandId.unsafe(commandId), dec)

  // ============================================================
  // Command Transformations
  // ============================================================

  /** Transform the response type of a Command.
    *
    * @param cmd The original command
    * @param f Function to transform the response after decoding
    * @tparam Req The request type (unchanged)
    * @tparam Res The original response type
    * @tparam Res2 The new response type
    * @return A new Command with transformed response decoder
    *
    * @example
    *   {{{
    * // Transform a String response to Int
    * given intVersion: Command[VersionReq, Int] =
    *   Command.mapResponse(stringVersionCmd)(_.toInt)
    *   }}}
    */
  def mapResponse[Req, Res, Res2](cmd: Command[Req, Res])(f: Res => Res2): Command[Req, Res2] =
    Impl[Req, Res2](
      cmd.id,
      cmd.encoder,
      cmd.decoder.map(f)
    )

  /** Transform the request type of a Command.
    *
    * @param cmd The original command
    * @param f Function to transform the request before encoding
    * @tparam Req The original request type
    * @tparam Req2 The new request type
    * @tparam Res The response type (unchanged)
    * @return A new Command with transformed request encoder
    *
    * @example
    *   {{{
    * // Accept a simpler type that gets transformed to the full request
    * given simpleSetTitle: Command[String, Unit] =
    *   Command.contramapRequest(setTitleCmd)(title => SetTitle("main", title))
    *   }}}
    */
  def contramapRequest[Req, Req2, Res](cmd: Command[Req, Res])(f: Req2 => Req): Command[Req2, Res] =
    Impl[Req2, Res](
      cmd.id,
      cmd.encoder.contramap(f),
      cmd.decoder
    )

  /** Transform both request and response types of a Command.
    *
    * @param cmd The original command
    * @param f Function to transform the request before encoding
    * @param g Function to transform the response after decoding
    * @return A new Command with both transformations applied
    */
  def bimap[Req, Req2, Res, Res2](cmd: Command[Req, Res])(
    f: Req2 => Req,
    g: Res => Res2
  ): Command[Req2, Res2] =
    Impl[Req2, Res2](
      cmd.id,
      cmd.encoder.contramap(f),
      cmd.decoder.map(g)
    )

  /** Transform the response type of a Command0.
    *
    * @param cmd The original zero-argument command
    * @param f Function to transform the response after decoding
    * @return A new Command0 with transformed response decoder
    */
  def mapResponse0[Res, Res2](cmd: Command0[Res])(f: Res => Res2): Command0[Res2] =
    Impl0[Res2](cmd.id, cmd.decoder.map(f))

  /** Create a copy of a command with a different command ID.
    *
    * Useful for testing, mocking, or creating variants of existing commands.
    *
    * @param cmd The original command
    * @param newId The new command identifier
    * @return A new Command with the specified ID
    */
  inline def withId[Req, Res](cmd: Command[Req, Res])(inline newId: String): Command[Req, Res] =
    Impl[Req, Res](CommandId.unsafe(newId), cmd.encoder, cmd.decoder)

  /** Create a copy of a zero-argument command with a different command ID.
    *
    * @param cmd The original command
    * @param newId The new command identifier
    * @return A new Command0 with the specified ID
    */
  inline def withId0[Res](cmd: Command0[Res])(inline newId: String): Command0[Res] =
    Impl0[Res](CommandId.unsafe(newId), cmd.decoder)

  // ============================================================
  // Extension Methods
  // ============================================================

  extension [Req, Res](cmd: Command[Req, Res])
    /** Transform the response type using the given function. */
    def mapRes[Res2](f: Res => Res2): Command[Req, Res2] =
      mapResponse(cmd)(f)

    /** Transform the request type using the given function. */
    def contramapReq[Req2](f: Req2 => Req): Command[Req2, Res] =
      contramapRequest(cmd)(f)

    /** Create a copy with a different command ID. */
    inline def withCommandId(inline newId: String): Command[Req, Res] =
      withId(cmd)(newId)
  end extension

  extension [Res](cmd: Command0[Res])
    /** Transform the response type using the given function. */
    def mapRes[Res2](f: Res => Res2): Command0[Res2] =
      mapResponse0(cmd)(f)

    /** Create a copy with a different command ID. */
    inline def withCommandId(inline newId: String): Command0[Res] =
      withId0(cmd)(newId)

  // ============================================================
  // Internal Helpers
  // ============================================================

  /** Helper to encode request parameters to JS.Any for IPC.
    *
    * Internal use only - converts request parameters to the format expected by Tauri's IPC layer.
    */
  private[api] inline def encodeRequest[Req](req: Req)(using enc: Encoder[Req]): js.Any =
    enc.encode(req)

  /** Helper to decode response from JS.Any.
    *
    * Internal use only - converts IPC response to the expected Scala type.
    */
  private[api] inline def decodeResponse[Res](value: js.Any)(using dec: Decoder[Res]): Either[String, Res] =
    dec.decode(value)
end Command
