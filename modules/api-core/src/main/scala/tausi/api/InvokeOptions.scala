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

import tausi.api.internal.InvokeOptionsJS

/** Options for Tauri command invocation.
  *
  * Pure data aggregate with no behaviour. All operations in companion.
  *
  * @param headers HTTP-style headers to include with the invoke request
  */
final case class InvokeOptions(headers: Map[String, String])

object InvokeOptions:
  /** Empty invoke options with no headers */
  val empty: InvokeOptions = InvokeOptions(Map.empty)

  /** Enable equality comparison for InvokeOptions */
  given CanEqual[InvokeOptions, InvokeOptions] = CanEqual.derived

  extension (opts: InvokeOptions)
    /** Convert to JS-compatible format for native invoke calls.
      *
      * Internal use only - converts to JS representation for IPC layer.
      *
      * Complexity: O(n) where n = number of headers
      */
    private[api] def toJS: InvokeOptionsJS =
      if opts.headers.isEmpty then InvokeOptionsJS.empty
      else InvokeOptionsJS(js.Dictionary(opts.headers.toSeq*))
end InvokeOptions
