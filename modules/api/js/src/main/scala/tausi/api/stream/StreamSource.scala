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
package tausi.api.stream

import tausi.api.TauriError

/** Type class for subscribing to effect streams via callbacks.
  *
  * This abstraction enables UI frameworks (such as Laminar) to consume streams from any effect
  * system (ZIO, Cats Effect, or custom) without depending on those effect systems directly. Effect
  * modules provide instances; UI modules consume them through the type class interface.
  *
  * The callback-based design allows bridging between push-based effect streams and push-based UI
  * observables without buffering or complex synchronisation.
  *
  * ==Type Parameters==
  *   - `S[_]`: The stream type constructor (e.g., `ZStream[Any, TauriError, *]` or `Stream[IO, *]`)
  *
  * ==Error Handling==
  * All errors are normalised to [[TauriError]] variants, ensuring consistent error handling
  * across effect systems. This follows the errors-as-values principle - errors are never
  * thrown or silently dropped.
  *
  * ==Thread Safety==
  * Implementations must ensure that:
  *   - Callbacks may be invoked from any thread
  *   - The returned [[StreamSubscription]] is safe to cancel from any thread
  *   - Cancellation stops further callback invocations (though in-flight calls may complete)
  *
  * ==Lifecycle==
  * The subscription lifecycle is:
  *   1. [[subscribe]] is called with callbacks
  *   2. `onNext` is invoked for each stream element
  *   3. Either `onError` or `onComplete` is invoked exactly once when the stream terminates
  *   4. The returned [[StreamSubscription]] can be used to cancel early
  *
  * @see
  *   [[StreamSubscription]] for the cancellation handle
  */
trait StreamSource[S[_]]:

  extension [A](stream: S[A])
    /** Subscribe to the stream with callbacks for elements, errors, and completion.
      *
      * The subscription begins immediately upon calling this method. Elements are delivered via
      * `onNext`, and the stream terminates with either `onError` or `onComplete` (never both).
      *
      * All errors are normalised to [[TauriError]] variants, ensuring consistent error handling
      * across effect systems. Upstream errors are wrapped in [[TauriError.StreamError]].
      *
      * @param onNext
      *   Callback invoked for each element emitted by the stream
      * @param onError
      *   Callback invoked if the stream fails with a [[TauriError]] (terminal)
      * @param onComplete
      *   Callback invoked when the stream completes successfully (terminal)
      * @return
      *   A [[StreamSubscription]] that can be used to cancel the subscription
      */
    def subscribe(onNext: A => Unit, onError: TauriError => Unit, onComplete: () => Unit): StreamSubscription
  end extension

end StreamSource

/** Companion for [[StreamSource]]. Provides summoner method. */
object StreamSource:

  /** Summoner for type class instances.
    *
    * @tparam S
    *   The stream type constructor
    * @return
    *   The [[StreamSource]] instance for `S`
    */
  inline def apply[S[_]](using source: StreamSource[S]): StreamSource[S] = source
