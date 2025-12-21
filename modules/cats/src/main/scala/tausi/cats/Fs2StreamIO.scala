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
package tausi.cats

import _root_.cats.effect.IO
import _root_.cats.effect.std.Dispatcher

import _root_.fs2.Stream

import tausi.api.TauriError
import tausi.api.stream.StreamSource
import tausi.api.stream.StreamSubscription

/** Type alias for fs2 streams with `IO` effect.
  *
  * This alias provides a concrete stream type suitable for the [[StreamSource]] type class,
  * enabling effect-agnostic stream integration with UI frameworks like Laminar.
  *
  * @tparam A
  *   The element type of the stream
  */
type Fs2StreamIO[A] = Stream[IO, A]

/** Companion for [[Fs2StreamIO]]. Provides [[StreamSource]] instance. */
object Fs2StreamIO:

  /** [[StreamSource]] instance for fs2 streams with IO effect.
    *
    * Requires an implicit [[Dispatcher]] to execute the stream. The dispatcher is typically
    * obtained from [[Dispatcher.parallel]] or [[Dispatcher.sequential]] within an IOApp or a
    * Resource.
    *
    * ==Usage==
    * {{{
    * import tausi.cats.Fs2StreamIO.given
    *
    * Dispatcher.parallel[IO].use { implicit dispatcher =>
    *   val stream: Fs2StreamIO[Int] = Stream(1, 2, 3)
    *   val sub = stream.subscribe(
    *     onNext = println,
    *     onError = err => println(s"Error: $$err"),
    *     onComplete = () => println("Done")
    *   )
    *   // ...
    * }
    * }}}
    */
  given streamSource(using dispatcher: Dispatcher[IO]): StreamSource[Fs2StreamIO] with
    extension [A](stream: Fs2StreamIO[A])
      override def subscribe(
        onNext: A => Unit,
        onError: TauriError => Unit,
        onComplete: () => Unit
      ): StreamSubscription =
        // Wrap upstream error into TauriError.StreamError
        inline def wrapError(t: Throwable): TauriError = t match
          case e: TauriError => e
          case e             => TauriError.StreamError(e.getMessage, Some(e))

        val effect: IO[Unit] =
          stream
            .evalMap(a => IO(onNext(a)))
            .compile
            .drain
            .attempt
            .flatMap {
              case Right(()) => IO(onComplete())
              case Left(err) => IO(onError(wrapError(err)))
            }

        val (_, cancel) = dispatcher.unsafeToFutureCancelable(effect)

        StreamSubscription { () =>
          cancel(): Unit
        }
    end extension
  end streamSource

end Fs2StreamIO
