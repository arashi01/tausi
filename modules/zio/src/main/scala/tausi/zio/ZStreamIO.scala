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
package tausi.zio

import _root_.zio.*
import _root_.zio.stream.ZStream

import tausi.api.TauriError
import tausi.api.stream.StreamSource
import tausi.api.stream.StreamSubscription

/** Type alias for ZIO streams with [[TauriError]] error channel.
  *
  * This alias provides a concrete stream type suitable for the [[StreamSource]] type class,
  * enabling effect-agnostic stream integration with UI frameworks like Laminar.
  *
  * All errors are represented as [[TauriError]] variants, ensuring consistent error handling
  * across the entire Tausi API. This follows the errors-as-values principle.
  *
  * @tparam A
  *   The element type of the stream
  */
type ZStreamIO[A] = ZStream[Any, TauriError, A]

/** Companion for [[ZStreamIO]]. Provides [[StreamSource]] instance. */
object ZStreamIO:

  /** [[StreamSource]] instance for ZIO streams.
    *
    * Requires an implicit [[Runtime]] to execute the stream. The runtime is typically provided by
    * the application entry point or a scoped resource.
    *
    * ==Usage==
    * {{{
    * import tausi.zio.ZStreamIO.given
    *
    * given Runtime[Any] = Runtime.default
    *
    * val stream: ZStreamIO[Int] = ZStream(1, 2, 3)
    * val sub = stream.subscribe(
    *   onNext = println,
    *   onError = err => println(s"Error: $$err"),
    *   onComplete = () => println("Done")
    * )
    * }}}
    */
  given streamSource(using runtime: Runtime[Any]): StreamSource[ZStreamIO] with
    extension [A](stream: ZStreamIO[A])
      override def subscribe(
        onNext: A => Unit,
        onError: TauriError => Unit,
        onComplete: () => Unit
      ): StreamSubscription =
        val effect: ZIO[Any, TauriError, Unit] =
          stream
            .runForeach(a => ZIO.succeed(onNext(a)))
            .foldCauseZIO(
              failure = cause =>
                ZIO.succeed {
                  cause.failureOption match
                    case Some(err) => onError(err)
                    case None      =>
                      cause.dieOption match
                        case Some(defect) =>
                          // Defects are unexpected failures - wrap them
                          onError(TauriError.StreamError(defect.getMessage, Some(defect)))
                        case None => onComplete() // Interrupted counts as complete
                },
              success = _ => ZIO.succeed(onComplete())
            )

        // Fork is synchronous - starts fiber and returns handle immediately
        val fibre: Fiber.Runtime[TauriError, Unit] = Unsafe.unsafe { (unsafe: Unsafe) =>
          given Unsafe = unsafe
          runtime.unsafe.fork(effect)
        }

        StreamSubscription { () =>
          // Use interruptFork (fire-and-forget) rather than interrupt.
          // The interrupt effect cannot be run synchronously in JS because
          // unsafe.run would block waiting for the fiber to complete.
          Unsafe.unsafe { (unsafe: Unsafe) =>
            given Unsafe = unsafe
            runtime.unsafe.run(fibre.interruptFork).getOrThrowFiberFailure()
          }
        }
    end extension
  end streamSource

end ZStreamIO
