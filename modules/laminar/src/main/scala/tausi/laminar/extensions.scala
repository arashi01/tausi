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
package tausi.laminar

import com.raquo.airstream.core.EventStream
import com.raquo.airstream.core.Signal
import com.raquo.airstream.eventbus.EventBus
import com.raquo.airstream.ownership.Owner
import com.raquo.airstream.ownership.Subscription
import com.raquo.airstream.state.Var

import tausi.api.TauriError
import tausi.api.stream.StreamSource
import tausi.api.stream.StreamSubscription

/** Extension methods for converting effect streams to Laminar observables.
  *
  * These extensions require a [[StreamSource]] instance for the stream type, which is typically
  * provided by importing a given from the effect module (e.g., `tausi.zio.ZStreamIO.given` or
  * `tausi.cats.Fs2StreamIO.given`).
  */
extension [S[_]: StreamSource, A](stream: S[A])

  // ==================
  // Tier 1: Unsafe
  // ==================

  /** Convert the effect stream to a Laminar EventStream.
    *
    * '''WARNING''': Errors are logged to console and dropped from the stream. The stream will
    * simply stop emitting on error. Use only for prototyping or when you explicitly don't care
    * about errors.
    *
    * Prefer [[toSignal]] or [[toStateSignal]] for proper error handling.
    *
    * @param owner
    *   The Laminar Owner that manages the subscription lifecycle. The subscription is
    *   automatically cancelled when the owner is killed (e.g., when a component unmounts).
    * @return
    *   A Laminar [[EventStream]] that emits values from the effect stream
    *
    * @example
    *   {{{
    * import tausi.laminar.*
    * import tausi.zio.ZStreamIO.given
    *
    * div(
    *   onMountUnmountCallback(
    *     mount = ctx => {
    *       given Owner = ctx.owner
    *       val eventStream = myStream.toStreamUnsafe
    *       // Use eventStream...
    *     }
    *   )
    * )
    *   }}}
    */
  def toStreamUnsafe(using owner: Owner): EventStream[A] =
    val bus = new EventBus[A]
    val subscription = stream.subscribe(
      onNext = a => bus.emit(a),
      onError = err => org.scalajs.dom.console.error(s"Stream error (dropped): $err"),
      onComplete = () => () // EventStream has no completion concept
    )
    registerCleanup(owner, subscription)
    bus.events

  /** Convert the effect stream to a Laminar Signal with an initial value.
    *
    * '''WARNING''': Errors are logged to console and the signal retains its last value. Use only
    * for prototyping or when you explicitly don't care about errors.
    *
    * Prefer the `toSignal(Either[...])` overload or [[toStateSignal]] for proper error handling.
    *
    * @param initial
    *   The initial value before the stream emits
    * @param owner
    *   The Laminar Owner that manages the subscription lifecycle
    * @return
    *   A Laminar [[Signal]] that holds the latest value from the effect stream
    */
  def toSignalUnsafe(initial: A)(using owner: Owner): Signal[A] =
    val variable = Var(initial)
    val subscription = stream.subscribe(
      onNext = a => variable.set(a),
      onError = err => org.scalajs.dom.console.error(s"Stream error (dropped): $err"),
      onComplete = () => ()
    )
    registerCleanup(owner, subscription)
    variable.signal

  // ==================
  // Tier 2: Either-based
  // ==================

  /** Convert the effect stream to a Laminar Signal with Either-based error handling.
    *
    * Errors from the stream are surfaced as `Left` values, allowing explicit error handling in
    * the UI. The initial value must also be an `Either`, typically `Left(error)` to indicate a
    * loading state or `Right(defaultValue)` for a default.
    *
    * @param initial
    *   The initial Either value. Use `Left(...)` for loading state or `Right(...)` for default.
    * @param owner
    *   The Laminar Owner that manages the subscription lifecycle
    * @return
    *   A Laminar [[Signal]] that holds `Right(value)` for success or `Left(error)` for failure
    *
    * @example
    *   {{{
    * import tausi.laminar.*
    * import tausi.zio.ZStreamIO.given
    *
    * // Use Left as loading indicator
    * val signal = myStream.toSignal(Left(TauriError.StreamError("Loading...")))
    *
    * div(
    *   child <-- signal.map {
    *     case Right(value) => span(s"Got: $$value")
    *     case Left(err) => span(cls := "loading", err.message)
    *   }
    * )
    *   }}}
    */
  def toSignal(initial: Either[TauriError, A])(using owner: Owner): Signal[Either[TauriError, A]] =
    val variable = Var(initial)
    val subscription = stream.subscribe(
      onNext = a => variable.set(Right(a)),
      onError = err => variable.set(Left(err)),
      onComplete = () => ()
    )
    registerCleanup(owner, subscription)
    variable.signal

  /** Convert the effect stream to a Laminar Signal with Option semantics.
    *
    * Values are wrapped in `Some`, and the initial value is `None`. Errors are surfaced via the
    * separate error callback parameter.
    *
    * @param onError
    *   Callback invoked when the stream fails
    * @param owner
    *   The Laminar Owner that manages the subscription lifecycle
    * @return
    *   A Laminar [[Signal]] that holds `Some(value)` when available or `None` initially
    *
    * @example
    *   {{{
    * import tausi.laminar.*
    * import tausi.zio.ZStreamIO.given
    *
    * val errorVar = Var[Option[TauriError]](None)
    * val signal = myStream.toSignal(err => errorVar.set(Some(err)))
    *
    * div(
    *   child.maybe <-- signal.map(_.map(v => span(s"Value: $$v"))),
    *   child.maybe <-- errorVar.signal.map(_.map(e => span(cls := "error", e.message)))
    * )
    *   }}}
    */
  def toSignal(onError: TauriError => Unit)(using owner: Owner): Signal[Option[A]] =
    val variable = Var[Option[A]](None)
    val subscription = stream.subscribe(
      onNext = a => variable.set(Some(a)),
      onError = onError,
      onComplete = () => ()
    )
    registerCleanup(owner, subscription)
    variable.signal

  // ==================
  // Tier 3: Full State
  // ==================

  /** Convert the effect stream to a Laminar Signal with full lifecycle visibility.
    *
    * This is the recommended method for production use. The [[StreamState]] ADT provides
    * visibility into all lifecycle phases: running (loading), values, errors, and completion.
    *
    * @param owner
    *   The Laminar Owner that manages the subscription lifecycle
    * @return
    *   A Laminar [[Signal]] holding the current [[StreamState]]
    *
    * @example
    *   {{{
    * import tausi.laminar.*
    * import tausi.zio.ZStreamIO.given
    *
    * div(
    *   child <-- myStream.toStateSignal.map {
    *     case StreamState.Running =>
    *       div(cls := "spinner", "Loading...")
    *     case StreamState.Value(data) =>
    *       renderData(data)
    *     case StreamState.Failed(err) =>
    *       div(cls := "error", s"Failed: $${err.message}")
    *     case StreamState.Completed =>
    *       div("Stream completed")
    *     case StreamState.CompletedWith(finalData) =>
    *       renderData(finalData)
    *   }
    * )
    *   }}}
    */
  def toStateSignal(using owner: Owner): Signal[StreamState[A]] =
    val variable = Var[StreamState[A]](StreamState.Running)
    val subscription = stream.subscribe(
      onNext = a => variable.set(StreamState.Value(a)),
      onError = err => variable.set(StreamState.Failed(err)),
      onComplete = () =>
        variable.now() match
          case StreamState.Value(lastValue) => variable.set(StreamState.CompletedWith(lastValue))
          case _                            => variable.set(StreamState.Completed)
    )
    registerCleanup(owner, subscription)
    variable.signal
  end toStateSignal

end extension

// Bridge StreamSubscription lifecycle to Laminar's Owner lifecycle
private inline def registerCleanup(
  owner: Owner,
  subscription: StreamSubscription
): Unit =
  val _ = new Subscription(owner, () => subscription.cancel())
