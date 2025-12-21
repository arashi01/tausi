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

/** Handle for cancelling a stream subscription.
  *
  * Represents a leaky resource that must be cleaned up when the subscription is no longer needed.
  * Calling [[cancel]] releases all resources associated with the subscription and stops receiving
  * new elements.
  *
  * [[cancel]] is idempotent and safe to call concurrently; only the first
  * invocation has any effect.
  *
  * @see
  *   [[StreamSource]] for creating subscriptions from effect streams
  */
trait StreamSubscription:

  /** Cancel the subscription and release all associated resources.
    *
    * This method is idempotent; calling it multiple times has no additional effect after the first
    * call. Once cancelled, no further elements will be delivered to the subscriber.
    *
    * Implementations must not throw exceptions.
    */
  def cancel(): Unit

/** Companion for [[StreamSubscription]]. Provides factory method. */
object StreamSubscription:

  /** Concrete implementation of [[StreamSubscription]]. */
  final class Impl @scala.annotation.publicInBinary private[StreamSubscription] (
    cancelFn: () => Unit
  ) extends StreamSubscription:
    private val cancelled = new java.util.concurrent.atomic.AtomicBoolean(false)

    override def cancel(): Unit =
      if cancelled.compareAndSet(false, true) then cancelFn()

  /** Create a subscription from a cancel function.
    *
    * The returned subscription guarantees `cancelFn` is invoked at most once,
    * even when [[cancel]] is called concurrently from multiple threads.
    *
    * @param cancelFn
    *   Function to invoke on cancellation. Must be idempotent and must not throw.
    */
  inline def apply(cancelFn: () => Unit): StreamSubscription =
    new Impl(cancelFn)

  given CanEqual[StreamSubscription, StreamSubscription] = CanEqual.derived
end StreamSubscription
