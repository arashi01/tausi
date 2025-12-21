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

import munit.FunSuite

import tausi.api.codec.Codec
import tausi.api.codec.Decoder
import tausi.api.codec.Encoder

class EventSuite extends FunSuite:
  // scalafix:off

  // Test payload types
  final case class TestPayload(name: String, count: Int)

  object TestPayload:
    given Codec[TestPayload] = Codec.derived
    given CanEqual[TestPayload, TestPayload] = CanEqual.derived

  final case class SimplePayload(value: String)

  object SimplePayload:
    given Codec[SimplePayload] = Codec.derived
    given CanEqual[SimplePayload, SimplePayload] = CanEqual.derived

  // ============================================================
  // Event.define Tests
  // ============================================================

  test("Event.define should create event with correct name"):
    val ev = Event.define[TestPayload]("test-event")
    assertEquals(ev.name.value, "test-event")

  test("Event.define should provide encoder"):
    val ev = Event.define[TestPayload]("test-event")
    val payload = TestPayload("test", 42)
    val encoded = ev.encoder.encode(payload)
    assert(encoded != null)

  test("Event.define should provide decoder"):
    val ev = Event.define[TestPayload]("test-event")
    val jsObj = js.Dynamic.literal("name" -> "test", "count" -> 42)
    val decoded = ev.decoder.decode(jsObj)
    assertEquals(decoded, Right(TestPayload("test", 42)))

  test("Event.define roundtrip encoding"):
    val ev = Event.define[TestPayload]("test-event")
    val original = TestPayload("roundtrip", 123)
    val encoded = ev.encoder.encode(original)
    val decoded = ev.decoder.decode(encoded)
    assertEquals(decoded, Right(original))

  // ============================================================
  // Event.defineWith Tests
  // ============================================================

  test("Event.defineWith should use custom encoder/decoder"):
    val customEncoder: Encoder[String] = (value: String) => s"custom:$value".asInstanceOf[js.Any]
    val customDecoder: Decoder[String] = (value: js.Any) =>
      val str = value.asInstanceOf[String]
      if str.startsWith("custom:") then Right(str.stripPrefix("custom:"))
      else Left("Invalid format")

    val ev = Event.defineWith[String]("custom-event")(customEncoder, customDecoder)
    assertEquals(ev.name.value, "custom-event")

    val encoded = ev.encoder.encode("test")
    assertEquals(encoded.asInstanceOf[String], "custom:test")

    val decoded = ev.decoder.decode("custom:hello")
    assertEquals(decoded, Right("hello"))

  // ============================================================
  // Event.define0 Tests
  // ============================================================

  test("Event.define0 should create Unit event"):
    val ev = Event.define0("no-payload-event")
    assertEquals(ev.name.value, "no-payload-event")

  test("Event.define0 should encode Unit"):
    val ev = Event.define0("no-payload-event")
    val encoded = ev.encoder.encode(())
    assert(js.isUndefined(encoded))

  test("Event.define0 should decode Unit"):
    val ev = Event.define0("no-payload-event")
    val decoded = ev.decoder.decode(js.undefined)
    assertEquals(decoded, Right(()))

  // ============================================================
  // Event Summoner Tests
  // ============================================================

  test("Event.apply should summon implicit event"):
    given testEvent: Event[TestPayload] = Event.define("implicit-test")
    val summoned = Event[TestPayload]
    assertEquals(summoned.name.value, "implicit-test")

  // ============================================================
  // Event Transformation Tests
  // ============================================================

  test("Event.mapPayload should transform payload types"):
    val stringEvent = Event.define[String]("string-event")
    val intEvent = Event.mapPayload(stringEvent)(_.toInt, _.toString)

    assertEquals(intEvent.name.value, "string-event")

    // Encode int as string
    val encoded = intEvent.encoder.encode(42)
    assertEquals(encoded.asInstanceOf[String], "42")

    // Decode string as int
    val decoded = intEvent.decoder.decode("123".asInstanceOf[js.Any])
    assertEquals(decoded, Right(123))

  test("Event.withName should create copy with new name"):
    val original = Event.define[TestPayload]("original-name")
    val renamed = Event.withName(original)("new-name")

    assertEquals(renamed.name.value, "new-name")

    // Should still encode/decode the same payload type
    val payload = TestPayload("test", 1)
    val encoded = renamed.encoder.encode(payload)
    val decoded = renamed.decoder.decode(encoded)
    assertEquals(decoded, Right(payload))

  // ============================================================
  // Extension Method Tests
  // ============================================================

  test("Event.imap extension should transform payload"):
    val stringEvent = Event.define[String]("ext-string-event")
    val intEvent = stringEvent.imap(_.toInt, _.toString)

    assertEquals(intEvent.name.value, "ext-string-event")

    val encoded = intEvent.encoder.encode(99)
    assertEquals(encoded.asInstanceOf[String], "99")

  test("Event.withEventName extension should rename"):
    val original = Event.define[SimplePayload]("ext-original")
    val renamed = original.withEventName("ext-renamed")

    assertEquals(renamed.name.value, "ext-renamed")
    assertEquals(original.name.value, "ext-original") // Original unchanged

  // ============================================================
  // Type Safety Tests
  // ============================================================

  test("Different event types should be distinguishable"):
    given event1: Event[TestPayload] = Event.define("event-1")
    given event2: Event[SimplePayload] = Event.define("event-2")

    // These should be different types and not conflict
    val e1 = Event[TestPayload]
    val e2 = Event[SimplePayload]

    assertEquals(e1.name.value, "event-1")
    assertEquals(e2.name.value, "event-2")

  // scalafix:on
end EventSuite
