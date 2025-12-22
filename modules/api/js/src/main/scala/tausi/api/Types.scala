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
  /** Create a CallbackId from a Tauri-provided integer.
    *
    * CallbackIds come from Tauri runtime and are always valid.
    *
    * @param id The callback identifier from Tauri
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
  /** Create a ResourceId from a Tauri-provided integer.
    *
    * ResourceIds come from Tauri runtime and are always valid.
    *
    * @param id The resource identifier from Tauri
    * @return ResourceId wrapping the integer
    */
  inline def unsafe(id: Int): ResourceId = id

  /** Enable equality comparison for ResourceId */
  given CanEqual[ResourceId, ResourceId] = CanEqual.derived

  /** Codec instance for ResourceId using iemap for validation. */
  given codec.Codec[ResourceId] = codec
    .Codec[Int]
    .iemap { n =>
      if n >= 0 then Right(unsafe(n))
      else Left(s"ResourceId must be non-negative, got: $n")
    }(_.toInt)

  extension (id: ResourceId)
    /** Convert ResourceId to its underlying integer value. */
    inline def toInt: Int = id

    /** String representation of the ResourceId. */
    inline def show: String = s"ResourceId($id)"
end ResourceId

// PermissionState removed - dead code, not used by any Tauri commands
// Will be added when Tauri adds permissions API
