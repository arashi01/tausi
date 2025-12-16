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

/** Typeclass for resources that can be closed.
  *
  * Provides a unified interface for resource cleanup, matching the upstream Tauri Resource trait's
  * close() method. This typeclass is effect-system agnostic and uses Future for async operations.
  *
  * Resources implementing this typeclass can be composed with effect system resource management:
  *   - cats-effect: Resource[IO, A]
  *   - ZIO: ZIO[Scope, E, A]
  *
  * Example:
  * {{{
  * import tausi.api.*
  *
  * // Automatic instance for all Resource subclasses
  * val resource: MyResource = ...
  * Closeable[MyResource].close(resource)  // Uses given instance
  *
  * // Or use extension method
  * resource.close()
  * }}}
  *
  * @tparam R The resource type
  */
trait Closeable[R]:
  /** Close the resource.
    *
    * Performs cleanup operations for the resource. This should be idempotent - calling it multiple
    * times should be safe. If the resource is already closed, this should be a no-op.
    *
    * @param resource The resource to close
    * @param ec Execution context for async operations
    * @return Future that completes when the resource is closed
    */
  def close(resource: R)(using ExecutionContext): Future[Unit]

object Closeable:
  /** Summon a Closeable instance for a given type.
    *
    * Example:
    * {{{
    * val closeable = Closeable[MyResource]
    * closeable.close(myResource)
    * }}}
    */
  def apply[R](using closeable: Closeable[R]): Closeable[R] = closeable

  /** Automatic Closeable instance for all Resource subclasses.
    *
    * This provides seamless integration with Tauri's resource system. Any type extending Resource
    * automatically gets a Closeable instance that delegates to the Resource.close() extension
    * method.
    */
  given resourceCloseable[R <: Resource]: Closeable[R] with
    def close(resource: R)(using ExecutionContext): Future[Unit] =
      resource.close()

  /** Extension methods for types with Closeable instances.
    *
    * Provides convenient syntax for closing resources:
    * {{{
    * import tausi.api.Closeable.syntax.*
    *
    * myResource.close()  // Uses Closeable instance
    * }}}
    */
  object syntax:
    extension [R](resource: R)(using closeable: Closeable[R])
      /** Close this resource using its Closeable instance.
        *
        * @param ec Execution context for async operations
        * @return Future that completes when the resource is closed
        */
      inline def close()(using ExecutionContext): Future[Unit] =
        closeable.close(resource)
end Closeable
