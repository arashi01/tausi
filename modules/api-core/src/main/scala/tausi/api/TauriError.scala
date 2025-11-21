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
import scala.util.control.NoStackTrace

/** The root error type for all Tauri-related errors. */
sealed trait TauriError extends Throwable with NoStackTrace with Product with Serializable

object TauriError:
  /** Error occurred during command invocation.
    *
    * @param command The command name that was invoked
    * @param message Description of what went wrong
    * @param cause Optional underlying exception
    */
  final case class InvokeError(
    command: String,
    message: String,
    cause: Option[Throwable]
  ) extends TauriError

  /** Error occurred during plugin operations.
    *
    * @param plugin The plugin identifier
    * @param message Description of what went wrong
    * @param cause Optional underlying exception
    */
  final case class PluginError(
    plugin: String,
    message: String,
    cause: Option[Throwable]
  ) extends TauriError

  /** Error occurred during permission operations.
    *
    * @param message Description of what went wrong
    * @param cause Optional underlying exception
    */
  final case class PermissionError(
    message: String,
    cause: Option[Throwable]
  ) extends TauriError

  /** Error occurred during resource management.
    *
    * @param resourceId The resource identifier
    * @param message Description of what went wrong
    * @param cause Optional underlying exception
    */
  final case class ResourceError(
    resourceId: ResourceId,
    message: String,
    cause: Option[Throwable]
  ) extends TauriError

  /** Error occurred during file path conversion.
    *
    * @param filePath The file path being converted
    * @param protocol The protocol being used
    * @param message Description of what went wrong
    * @param cause Optional underlying exception
    */
  final case class ConversionError(
    filePath: String,
    protocol: String,
    message: String,
    cause: Option[Throwable]
  ) extends TauriError

  /** Error occurred during callback operations.
    *
    * @param callbackId The callback identifier
    * @param message Description of what went wrong
    * @param cause Optional underlying exception
    */
  final case class CallbackError(
    callbackId: CallbackId,
    message: String,
    cause: Option[Throwable]
  ) extends TauriError

  /** Error occurred during channel operations.
    *
    * @param channelId The channel identifier
    * @param message Description of what went wrong
    * @param cause Optional underlying exception
    */
  final case class ChannelError(
    channelId: CallbackId,
    message: String,
    cause: Option[Throwable]
  ) extends TauriError

  /** Generic error for cases not covered by specific error types.
    *
    * @param message Description of what went wrong
    * @param cause Optional underlying exception
    */
  final case class GenericError(
    message: String,
    cause: Option[Throwable]
  ) extends TauriError

  /** Error indicating Tauri runtime is not available.
    *
    * This typically occurs when code is run outside a Tauri application.
    *
    * @param message Description of what went wrong
    */
  final case class TauriNotAvailableError(message: String) extends TauriError

  object InvokeError:
    /** Create InvokeError without cause */
    def apply(command: String, message: String): InvokeError =
      new InvokeError(command, message, None)

  object PluginError:
    /** Create PluginError without cause */
    def apply(plugin: String, message: String): PluginError =
      new PluginError(plugin, message, None)

  object PermissionError:
    /** Create PermissionError without cause */
    def apply(message: String): PermissionError =
      new PermissionError(message, None)

  object ResourceError:
    /** Create ResourceError without cause */
    def apply(resourceId: ResourceId, message: String): ResourceError =
      new ResourceError(resourceId, message, None)

  object ConversionError:
    /** Create ConversionError without cause */
    def apply(filePath: String, protocol: String, message: String): ConversionError =
      new ConversionError(filePath, protocol, message, None)

  object CallbackError:
    /** Create CallbackError without cause */
    def apply(callbackId: CallbackId, message: String): CallbackError =
      new CallbackError(callbackId, message, None)

  object ChannelError:
    /** Create ChannelError without cause */
    def apply(channelId: CallbackId, message: String): ChannelError =
      new ChannelError(channelId, message, None)

  object GenericError:
    /** Create GenericError without cause */
    def apply(message: String): GenericError =
      new GenericError(message, None)

  object TauriNotAvailableError:
    /** Default Tauri not available error with standard message */
    def default: TauriNotAvailableError =
      TauriNotAvailableError(
        "Tauri runtime is not available. Ensure this code runs within a Tauri application."
      )

  extension (error: TauriError)
    /** Extract human-readable error message from any TauriError variant. */
    def message: String = error match
      case e: InvokeError            => e.message
      case e: PluginError            => e.message
      case e: PermissionError        => e.message
      case e: ResourceError          => e.message
      case e: ConversionError        => e.message
      case e: CallbackError          => e.message
      case e: ChannelError           => e.message
      case e: GenericError           => e.message
      case e: TauriNotAvailableError => e.message

    /** Extract optional underlying cause from any TauriError variant. */
    def cause: Option[Throwable] = error match
      case e: InvokeError            => e.cause
      case e: PluginError            => e.cause
      case e: PermissionError        => e.cause
      case e: ResourceError          => e.cause
      case e: ConversionError        => e.cause
      case e: CallbackError          => e.cause
      case e: ChannelError           => e.cause
      case e: GenericError           => e.cause
      case _: TauriNotAvailableError => None
  end extension

  // ====================
  // Helper Constructors
  // ====================

  /** Wrap any Throwable into a TauriError.
    *
    * If already a TauriError, returns as-is. Otherwise, wraps in GenericError.
    *
    * @param t The throwable to wrap
    * @return TauriError variant
    */
  def fromThrowable(t: Throwable): TauriError = t match
    case e: TauriError => e
    case e             => GenericError(s"Unexpected error: ${e.getMessage}", Some(e))

  /** Wrap JavaScript error into a TauriError.
    *
    * Handles various JavaScript error representations safely.
    *
    * @param error The JavaScript error object
    * @param context Descriptive context for error message
    * @return GenericError wrapping the JS error
    */
  def fromJSError(error: Any, context: String): TauriError =
    error match
      case e: js.JavaScriptException =>
        GenericError(s"$context: ${e.getMessage}", Some(e))
      case e: js.Error =>
        GenericError(s"$context: ${e.message}", None)
      case e: Throwable =>
        GenericError(s"$context: ${e.getMessage}", Some(e))
      case _ =>
        GenericError(s"$context: Unknown JavaScript error", None)

  given CanEqual[TauriError, TauriError] = CanEqual.derived
end TauriError
