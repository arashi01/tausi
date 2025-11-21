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

/** Opaque type representing callback identifiers.
  *
  * Callback IDs are used to uniquely identify callbacks registered with the Tauri runtime.
  */
opaque type CallbackId = Int

object CallbackId:
  /** Create a CallbackId from an integer with validation.
    *
    * Validates that the integer is non-negative (Tauri callback IDs are always >= 0).
    *
    * @param id The raw integer identifier
    * @return Right(CallbackId) if valid, Left(error message) otherwise
    */
  def fromInt(id: Int): Either[String, CallbackId] =
    if id >= 0 then Right(id)
    else Left(s"CallbackId must be non-negative, got: $id")

  /** Unsafely create a CallbackId from an integer without validation.
    *
    * Use only when the integer is known to be valid (e.g., from trusted Tauri internals). For
    * user-provided or untrusted integers, use [[fromInt]] instead.
    *
    * @param id The raw integer identifier
    * @return CallbackId wrapping the integer
    */
  inline def unsafe(id: Int): CallbackId = id

  /** Enable equality comparison for CallbackId */
  given CanEqual[CallbackId, CallbackId] = CanEqual.derived

  extension (id: CallbackId)
    /** Convert CallbackId to its underlying integer value. */
    inline def toInt: Int = id

    /** String representation of the CallbackId. */
    inline def show: String = s"CallbackId($id)"
end CallbackId

/** Opaque type for resource identifiers.
  *
  * Resource IDs are used to uniquely identify Rust-backed resources managed by Tauri. The opaque
  * type ensures type safety and prevents accidental confusion with other integer IDs.
  *
  * Construction must go through validated smart constructors in the companion object.
  */
opaque type ResourceId = Int

object ResourceId:
  /** Create a ResourceId from an integer with validation.
    *
    * Validates that the integer is non-negative (Tauri resource IDs are always >= 0).
    *
    * @param id The raw integer identifier
    * @return Right(ResourceId) if valid, Left(error message) otherwise
    */
  def fromInt(id: Int): Either[String, ResourceId] =
    if id >= 0 then Right(id)
    else Left(s"ResourceId must be non-negative, got: $id")

  /** Unsafely create a ResourceId from an integer without validation.
    *
    * Use only when the integer is known to be valid (e.g., from trusted Tauri internals). For
    * user-provided or untrusted integers, use [[fromInt]] instead.
    *
    * @param id The raw integer identifier
    * @return ResourceId wrapping the integer
    */
  inline def unsafe(id: Int): ResourceId = id

  /** Enable equality comparison for ResourceId */
  given CanEqual[ResourceId, ResourceId] = CanEqual.derived

  extension (id: ResourceId)
    /** Convert ResourceId to its underlying integer value. */
    inline def toInt: Int = id

    /** String representation of the ResourceId. */
    inline def show: String = s"ResourceId($id)"
end ResourceId

/** Permission state enumeration.
  *
  * Represents the various states a permission can be in within the Tauri application. This is a
  * pure data enum with all behaviour in the companion object.
  */
enum PermissionState:
  /** Permission has been granted */
  case Granted

  /** Permission has been denied */
  case Denied

  /** User needs to be prompted for permission */
  case Prompt

  /** User needs to be prompted with a rationale explaining why permission is needed */
  case PromptWithRationale
end PermissionState

object PermissionState:
  /** Parse a PermissionState from its string representation with validation.
    *
    * @param s The string to parse
    * @return Right(PermissionState) if valid, Left(error message) otherwise
    */
  def fromString(s: String): Either[String, PermissionState] = s match
    case "granted"               => Right(Granted)
    case "denied"                => Right(Denied)
    case "prompt"                => Right(Prompt)
    case "prompt-with-rationale" => Right(PromptWithRationale)
    case other                   => Left(s"Unknown permission state: $other")

  /** Enable equality comparison for PermissionState */
  given CanEqual[PermissionState, PermissionState] = CanEqual.derived

  extension (state: PermissionState)
    /** Convert PermissionState to its string representation. */
    def asString: String = state match
      case Granted             => "granted"
      case Denied              => "denied"
      case Prompt              => "prompt"
      case PromptWithRationale => "prompt-with-rationale"

    /** Check if permission is granted. */
    inline def isGranted: Boolean = state == Granted

    /** Check if permission is denied. */
    inline def isDenied: Boolean = state == Denied

    /** Check if user needs to be prompted. */
    inline def needsPrompt: Boolean =
      state == Prompt || state == PromptWithRationale
  end extension
end PermissionState
