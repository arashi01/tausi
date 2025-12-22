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

import tausi.api.TausiError

/** State ADT for stream-to-signal conversion with full lifecycle visibility.
  *
  * Represents the complete lifecycle of a stream subscription, enabling UI components to display
  * appropriate feedback for loading states, errors, and successful values.
  *
  * ==States==
  *   - [[Running]]: Stream is active but no value has been received yet (loading state)
  *   - [[Value]]: Stream emitted a value and is still running
  *   - [[Failed]]: Stream terminated with an error (represented as [[TausiError]])
  *   - [[Completed]]: Stream completed successfully without emitting a final value
  *   - [[CompletedWith]]: Stream completed successfully with a final value
  *
  * ==Usage==
  * {{{
  * import tausi.laminar.*
  * import tausi.zio.ZStreamIO.given
  *
  * val stateSignal: Signal[StreamState[Int]] = myStream.toStateSignal
  *
  * div(
  *   child <-- stateSignal.map {
  *     case StreamState.Running => span("Loading...")
  *     case StreamState.Value(n) => span(s"Current: $$n")
  *     case StreamState.Failed(err) => span(cls := "error", s"Error: $${err.message}")
  *     case StreamState.Completed => span("Done")
  *     case StreamState.CompletedWith(n) => span(s"Final: $$n")
  *   }
  * )
  * }}}
  *
  * @tparam A
  *   The value type
  */
enum StreamState[+A]:

  /** Stream is running but no value has been received yet.
    *
    * This is the initial state before any elements are emitted. Use this to display loading
    * indicators or placeholder content.
    */
  case Running

  /** Stream emitted a value and is still running.
    *
    * @param value
    *   The most recent value emitted by the stream
    */
  case Value(value: A)

  /** Stream terminated with an error.
    *
    * @param error
    *   The [[TausiError]] that caused the stream to fail
    */
  case Failed(error: TausiError)

  /** Stream completed successfully without emitting a final value.
    *
    * This occurs when the stream completes but no value was ever emitted, or when the completion
    * should be treated as a distinct terminal state.
    */
  case Completed

  /** Stream completed successfully with a final value.
    *
    * @param lastValue
    *   The last value emitted before completion
    */
  case CompletedWith(lastValue: A)

end StreamState

/** Companion for [[StreamState]]. Provides extension methods and instances. */
object StreamState:

  given [A]: CanEqual[StreamState[A], StreamState[A]] = CanEqual.derived

  extension [A](state: StreamState[A])

    /** Check if the stream is still running (either no value yet or has emitted values). */
    inline def isRunning: Boolean = state match
      case Running  => true
      case Value(_) => true
      case _        => false

    /** Check if the stream has terminated (either successfully or with an error). */
    inline def isTerminated: Boolean = !isRunning

    /** Check if the stream terminated with an error. */
    inline def isFailed: Boolean = state match
      case Failed(_) => true
      case _         => false

    /** Check if the stream completed successfully (with or without a final value). */
    inline def isCompleted: Boolean = state match
      case Completed        => true
      case CompletedWith(_) => true
      case _                => false

    /** Get the current value if one exists. */
    def valueOption: Option[A] = state match
      case Value(v)         => Some(v)
      case CompletedWith(v) => Some(v)
      case _                => None

    /** Get the error if the stream failed. */
    def errorOption: Option[TausiError] = state match
      case Failed(e) => Some(e)
      case _         => None

    /** Fold over all possible states.
      *
      * @param onRunning
      *   Handler for initial running state
      * @param onValue
      *   Handler for value state
      * @param onFailed
      *   Handler for error state
      * @param onCompleted
      *   Handler for completion without value
      * @param onCompletedWith
      *   Handler for completion with value
      */
    inline def fold[B](
      onRunning: => B,
      onValue: A => B,
      onFailed: TausiError => B,
      onCompleted: => B,
      onCompletedWith: A => B
    ): B = state match
      case Running          => onRunning
      case Value(v)         => onValue(v)
      case Failed(e)        => onFailed(e)
      case Completed        => onCompleted
      case CompletedWith(v) => onCompletedWith(v)

  end extension

end StreamState
