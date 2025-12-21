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

import tausi.api.codec.Codec
import tausi.api.codec.Decoder
import tausi.api.codec.Encoder

/** Type-safe event definition coupling name with payload type.
  *
  * Events are the event-system analogue of [[Command]]. Each Event instance defines the event name
  * and its payload type with associated codecs, ensuring type-safe emit and listen operations.
  *
  * Events are typically defined as given instances using the factory methods in [[Event$]]:
  *
  * @example
  *   {{{
  * import tausi.api.Event
  * import tausi.api.codec.Codec
  *
  * // Define a payload type
  * final case class SurveySubmission(data: String) derives Codec
  *
  * // Create the event instance using the factory method
  * given surveySubmitted: Event[SurveySubmission] =
  *   Event.define[SurveySubmission]("survey-submitted")
  *
  * // Use with type-safe listen/emit
  * events.listen { msg => println(msg.payload.data) }
  * events.emit(SurveySubmission("test"))
  *   }}}
  *
  * @tparam A
  *   The payload type (use Unit for events without payload)
  * @see
  *   [[Event$]] for factory methods and companion object utilities
  */
trait Event[A]:
  /** The event identifier. */
  def name: EventName

  /** Encoder for emitting payloads. */
  given encoder: Encoder[A]

  /** Decoder for receiving payloads. */
  given decoder: Decoder[A]

/** Companion for [[Event]]. Provides factory methods and instances.
  *
  * @see
  *   [[Event]] for trait documentation
  */
object Event:

  /** Summon an Event instance from implicit scope. */
  inline def apply[A](using ev: Event[A]): Event[A] = ev

  // ============================================================
  // Implementation Class
  // ============================================================

  /** Concrete implementation of Event for factory methods.
    *
    * Uses `@publicInBinary` to allow inline factory methods to instantiate while keeping the class
    * private to the Event companion object.
    */
  final class Impl[A] @scala.annotation.publicInBinary private[Event] (
    val name: EventName,
    enc: Encoder[A],
    dec: Decoder[A]
  ) extends Event[A]:
    given encoder: Encoder[A] = enc
    given decoder: Decoder[A] = dec

  object Impl:
    given [A]: CanEqual[Impl[A], Impl[A]] = CanEqual.derived

  // ============================================================
  // Factory Methods
  // ============================================================

  /** Create an event with automatic codec resolution.
    *
    * This factory method simplifies defining events by automatically resolving Encoder and Decoder
    * instances from the provided Codec.
    *
    * @param eventName
    *   The event name string (e.g., "survey-submitted")
    * @tparam A
    *   The payload type (must have a Codec instance)
    * @return
    *   An Event instance ready for use with listen/emit
    *
    * @example
    *   {{{
    * final case class SurveySubmission(data: String) derives Codec
    *
    * given surveySubmitted: Event[SurveySubmission] =
    *   Event.define[SurveySubmission]("survey-submitted")
    *
    * // Use with type-safe emit
    * events.emit(SurveySubmission("test"))
    *   }}}
    */
  inline def define[A](inline eventName: String)(using codec: Codec[A]): Event[A] =
    Impl[A](EventName.unsafe(eventName), codec, codec)

  /** Create an event with explicit encoder and decoder.
    *
    * Use this variant when you have separate Encoder and Decoder instances instead of unified
    * Codec instances.
    *
    * @param eventName
    *   The event name string
    * @param enc
    *   Encoder for the payload type
    * @param dec
    *   Decoder for the payload type
    * @tparam A
    *   The payload type
    * @return
    *   An Event instance ready for use with listen/emit
    *
    * @example
    *   {{{
    * given myEvent: Event[MyPayload] =
    *   Event.defineWith[MyPayload]("my-event")(myEncoder, myDecoder)
    *   }}}
    */
  inline def defineWith[A](inline eventName: String)(enc: Encoder[A], dec: Decoder[A]): Event[A] =
    Impl[A](EventName.unsafe(eventName), enc, dec)

  /** Create an event with no payload.
    *
    * Use this for events that carry no data, only signal occurrence.
    *
    * @param eventName
    *   The event name string
    * @return
    *   An Event[Unit] instance
    *
    * @example
    *   {{{
    * given backendReady: Event[Unit] = Event.define0("backend-ready")
    *
    * // Emit without payload
    * events.emit(())
    *   }}}
    */
  inline def define0(inline eventName: String): Event[Unit] =
    Impl[Unit](EventName.unsafe(eventName), summon[Encoder[Unit]], summon[Decoder[Unit]])

  // ============================================================
  // Event Transformations
  // ============================================================

  /** Transform the payload type of an Event.
    *
    * @param ev
    *   The original event
    * @param f
    *   Function to transform the payload after decoding (for listen)
    * @param g
    *   Function to transform the payload before encoding (for emit)
    * @tparam A
    *   The original payload type
    * @tparam B
    *   The new payload type
    * @return
    *   A new Event with transformed payload type
    *
    * @example
    *   {{{
    * // Transform a String event to an Int event
    * given intEvent: Event[Int] =
    *   Event.mapPayload(stringEvent)(_.toInt, _.toString)
    *   }}}
    */
  def mapPayload[A, B](ev: Event[A])(f: A => B, g: B => A): Event[B] =
    Impl[B](ev.name, ev.encoder.contramap(g), ev.decoder.map(f))

  /** Create a copy of an event with a different event name.
    *
    * Useful for creating variants of existing events or for testing.
    *
    * @param ev
    *   The original event
    * @param newName
    *   The new event name
    * @return
    *   A new Event with the specified name
    */
  inline def withName[A](ev: Event[A])(inline newName: String): Event[A] =
    Impl[A](EventName.unsafe(newName), ev.encoder, ev.decoder)

  // ============================================================
  // Extension Methods
  // ============================================================

  extension [A](ev: Event[A])
    /** Transform the payload type using the given functions. */
    def imap[B](f: A => B, g: B => A): Event[B] =
      mapPayload(ev)(f, g)

    /** Create a copy with a different event name. */
    inline def withEventName(inline newName: String): Event[A] =
      withName(ev)(newName)
end Event
