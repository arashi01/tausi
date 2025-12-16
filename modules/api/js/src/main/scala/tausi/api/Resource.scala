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

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/** Base class for all Tauri resources.
  *
  * Resources represent server-side objects in the Rust backend that require explicit cleanup. Each
  * resource has a unique ResourceId that identifies it in the Tauri runtime.
  *
  * Subclasses should extend this to provide type-safe wrappers around specific Tauri resource
  * types. Resources must be explicitly closed via `close()` to free backend resources.
  *
  * @param rid The unique resource identifier
  */
abstract class Resource(val rid: ResourceId)

object Resource:
  extension (resource: Resource)
    /** Close this resource.
      *
      * Closes the underlying resource in the Rust backend, freeing any associated system resources.
      * After closing, the resource cannot be used.
      *
      * @param ec Execution context for async operations
      * @return Future containing Either a TauriError or Unit
      */
    def close()(using ec: ExecutionContext): Future[Unit] =
      // Import core in method to avoid circular dependency
      tausi.api.core.closeResource(resource.rid)
  end extension
end Resource
