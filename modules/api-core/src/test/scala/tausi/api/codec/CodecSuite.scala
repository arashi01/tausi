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
package tausi.api.codec

import scala.scalajs.js

import munit.FunSuite

/** Comprehensive test suite for codec implementation.
  *
  * Tests cover:
  *   - Primitive type encoding/decoding
  *   - Collection type handling
  *   - Option type handling
  *   - Product type (case class) derivation
  *   - Sum type (enum) derivation
  *   - Nested structures
  *   - Error handling and edge cases
  */
class CodecSuite extends FunSuite:
  // scalafix:off DisableSyntax.asInstanceOf, DisableSyntax.null
  // ====================
  // Primitive Encoders
  // ====================

  test("Encoder[String] should encode strings correctly"):
    val encoder = Encoder[String]
    val result = encoder.encode("hello")
    assertEquals(result.asInstanceOf[String], "hello")

  test("Encoder[Int] should encode integers correctly"):
    val encoder = Encoder[Int]
    val result = encoder.encode(42)
    assertEquals(result.asInstanceOf[Int], 42)

  test("Encoder[Long] should encode longs as doubles"):
    val encoder = Encoder[Long]
    val result = encoder.encode(1234567890L)
    assertEquals(result.asInstanceOf[Double], 1234567890.0)

  test("Encoder[Double] should encode doubles correctly"):
    val encoder = Encoder[Double]
    val result = encoder.encode(3.14)
    assertEquals(result.asInstanceOf[Double], 3.14)

  test("Encoder[Boolean] should encode booleans correctly"):
    val encoder = Encoder[Boolean]
    assert(encoder.encode(true).asInstanceOf[Boolean])
    assert(!encoder.encode(false).asInstanceOf[Boolean])

  test("Encoder[Unit] should encode to undefined"):
    val encoder = Encoder[Unit]
    val result = encoder.encode(())
    assert(js.isUndefined(result))

  // ====================
  // Primitive Decoders
  // ====================

  test("Decoder[String] should decode strings correctly"):
    val decoder = Decoder[String]
    val result = decoder.decode("test".asInstanceOf[js.Any])
    assertEquals(result, Right("test"))

  test("Decoder[String] should fail on non-string"):
    val decoder = Decoder[String]
    val result = decoder.decode(42.asInstanceOf[js.Any])
    assert(result.isLeft)
    assert(result.left.exists(_.contains("Expected string")))

  test("Decoder[Int] should decode valid integers"):
    val decoder = Decoder[Int]
    val result = decoder.decode(42.asInstanceOf[js.Any])
    assertEquals(result, Right(42))

  test("Decoder[Int] should fail on non-integer doubles"):
    val decoder = Decoder[Int]
    val result = decoder.decode(3.14.asInstanceOf[js.Any])
    assert(result.isLeft)
    assert(result.left.exists(_.contains("not a valid Int")))

  test("Decoder[Long] should decode numbers as longs"):
    val decoder = Decoder[Long]
    val result = decoder.decode(123456.0.asInstanceOf[js.Any])
    assertEquals(result, Right(123456L))

  test("Decoder[Double] should decode numbers"):
    val decoder = Decoder[Double]
    val result = decoder.decode(3.14.asInstanceOf[js.Any])
    assertEquals(result, Right(3.14))

  test("Decoder[Boolean] should decode booleans"):
    val decoder = Decoder[Boolean]
    assertEquals(decoder.decode(true.asInstanceOf[js.Any]), Right(true))
    assertEquals(decoder.decode(false.asInstanceOf[js.Any]), Right(false))

  test("Decoder[Boolean] should fail on non-boolean"):
    val decoder = Decoder[Boolean]
    val result = decoder.decode("true".asInstanceOf[js.Any])
    assert(result.isLeft)

  test("Decoder[Unit] should always succeed"):
    val decoder = Decoder[Unit]
    assertEquals(decoder.decode(js.undefined), Right(()))
    assertEquals(decoder.decode(null), Right(()))
    assertEquals(decoder.decode("anything".asInstanceOf[js.Any]), Right(()))

  // ====================
  // Collection Encoders
  // ====================

  test("Encoder[List[Int]] should encode lists correctly"):
    val encoder = Encoder[List[Int]]
    val result = encoder.encode(List(1, 2, 3))
    val arr = result.asInstanceOf[js.Array[Int]]
    assertEquals(arr.length, 3)
    assertEquals(arr(0), 1)
    assertEquals(arr(1), 2)
    assertEquals(arr(2), 3)

  test("Encoder[List[String]] should encode string lists"):
    val encoder = Encoder[List[String]]
    val result = encoder.encode(List("a", "b", "c"))
    val arr = result.asInstanceOf[js.Array[String]]
    assertEquals(arr.length, 3)
    assertEquals(arr(0), "a")

  test("Encoder[Vector[Int]] should encode vectors"):
    val encoder = Encoder[Vector[Int]]
    val result = encoder.encode(Vector(10, 20))
    val arr = result.asInstanceOf[js.Array[Int]]
    assertEquals(arr.length, 2)
    assertEquals(arr(0), 10)

  test("Encoder[Map[String, Int]] should encode maps as dictionaries"):
    val encoder = Encoder[Map[String, Int]]
    val result = encoder.encode(Map("a" -> 1, "b" -> 2))
    val dict = result.asInstanceOf[js.Dictionary[Int]]
    assertEquals(dict("a"), 1)
    assertEquals(dict("b"), 2)

  // ====================
  // Collection Decoders
  // ====================

  test("Decoder[List[Int]] should decode arrays"):
    val decoder = Decoder[List[Int]]
    val arr = js.Array(1, 2, 3)
    val result = decoder.decode(arr.asInstanceOf[js.Any])
    assertEquals(result, Right(List(1, 2, 3)))

  test("Decoder[List[Int]] should fail on non-array"):
    val decoder = Decoder[List[Int]]
    val result = decoder.decode("not an array".asInstanceOf[js.Any])
    assert(result.isLeft)
    assert(result.left.exists(_.contains("Expected array")))

  test("Decoder[List[Int]] should fail if element decode fails"):
    val decoder = Decoder[List[Int]]
    val arr = js.Array(1, "not a number", 3)
    val result = decoder.decode(arr.asInstanceOf[js.Any])
    assert(result.isLeft)
    assert(result.left.exists(_.contains("Array element")))

  test("Decoder[Vector[String]] should decode to vector"):
    val decoder = Decoder[Vector[String]]
    val arr = js.Array("x", "y")
    val result = decoder.decode(arr.asInstanceOf[js.Any])
    assertEquals(result, Right(Vector("x", "y")))

  // ====================
  // Option Encoders/Decoders
  // ====================

  test("Encoder[Option[Int]] should encode Some as value"):
    val encoder = Encoder[Option[Int]]
    val result = encoder.encode(Some(42))
    assertEquals(result.asInstanceOf[Int], 42)

  test("Encoder[Option[Int]] should encode None as undefined"):
    val encoder = Encoder[Option[Int]]
    val result = encoder.encode(None)
    assert(js.isUndefined(result))

  test("Decoder[Option[Int]] should decode null/undefined to None"):
    val decoder = Decoder[Option[Int]]
    assertEquals(decoder.decode(null), Right(None))
    assertEquals(decoder.decode(js.undefined), Right(None))

  test("Decoder[Option[Int]] should decode value to Some"):
    val decoder = Decoder[Option[Int]]
    val result = decoder.decode(42.asInstanceOf[js.Any])
    assertEquals(result, Right(Some(42)))

  test("Decoder[Option[Int]] should fail if inner decode fails"):
    val decoder = Decoder[Option[Int]]
    val result = decoder.decode("not a number".asInstanceOf[js.Any])
    assert(result.isLeft)

  // ====================
  // Product Type Derivation
  // ====================

  case class Person(name: String, age: Int)

  object Person:
    given CanEqual[Person, Person] = CanEqual.derived
    given Codec[Person] = Codec.derived

  test("Derived Encoder[Person] should encode case class to object"):
    val encoder = Encoder[Person]
    val result = encoder.encode(Person("Alice", 30))
    val obj = result.asInstanceOf[js.Dictionary[js.Any]]
    assertEquals(obj("name").asInstanceOf[String], "Alice")
    assertEquals(obj("age").asInstanceOf[Int], 30)

  test("Derived Decoder[Person] should decode object to case class"):
    val decoder = Decoder[Person]
    val obj = js.Dictionary[js.Any]("name" -> "Bob", "age" -> 25.0)
    val result = decoder.decode(obj.asInstanceOf[js.Any])
    assertEquals(result, Right(Person("Bob", 25)))

  test("Derived Decoder[Person] should fail on missing field"):
    val decoder = Decoder[Person]
    val obj = js.Dictionary[js.Any]("name" -> "Charlie")
    val result = decoder.decode(obj.asInstanceOf[js.Any])
    assert(result.isLeft)
    assert(result.left.exists(_.contains("Field 'age'")))

  test("Derived Decoder[Person] should fail on wrong field type"):
    val decoder = Decoder[Person]
    val obj = js.Dictionary[js.Any]("name" -> "Dave", "age" -> "not a number")
    val result = decoder.decode(obj.asInstanceOf[js.Any])
    assert(result.isLeft)
    assert(result.left.exists(_.contains("Field 'age'")))

  test("Derived Decoder[Person] should fail on non-object"):
    val decoder = Decoder[Person]
    val result = decoder.decode("not an object".asInstanceOf[js.Any])
    assert(result.isLeft)
    assert(result.left.exists(_.contains("Expected object")))

  // ====================
  // Nested Structures
  // ====================

  case class Address(street: String, city: String)

  object Address:
    given CanEqual[Address, Address] = CanEqual.derived
    given Codec[Address] = Codec.derived

  case class Employee(name: String, address: Address, projects: List[String])

  object Employee:
    given CanEqual[Employee, Employee] = CanEqual.derived
    given Codec[Employee] = Codec.derived

  test("Nested product types should encode correctly"):
    val encoder = Encoder[Employee]
    val emp = Employee("John", Address("123 Main", "NYC"), List("A", "B"))
    val result = encoder.encode(emp)
    val obj = result.asInstanceOf[js.Dictionary[js.Any]]
    assertEquals(obj("name").asInstanceOf[String], "John")

    val addr = obj("address").asInstanceOf[js.Dictionary[js.Any]]
    assertEquals(addr("street").asInstanceOf[String], "123 Main")

    val projects = obj("projects").asInstanceOf[js.Array[String]]
    assertEquals(projects.length, 2)

  test("Nested product types should decode correctly"):
    val decoder = Decoder[Employee]
    val obj = js.Dictionary[js.Any](
      "name" -> "Jane",
      "address" -> js.Dictionary[js.Any]("street" -> "456 Oak", "city" -> "LA"),
      "projects" -> js.Array("X", "Y", "Z")
    )
    val result = decoder.decode(obj.asInstanceOf[js.Any])
    assertEquals(result, Right(Employee("Jane", Address("456 Oak", "LA"), List("X", "Y", "Z"))))

  // ====================
  // Sum Type (Enum) Derivation
  // ====================

  enum Color derives Codec:
    case Red
    case Green
    case Blue

  object Color:
    given CanEqual[Color, Color] = CanEqual.derived

  test("Derived Encoder for simple enum should encode as string"):
    val encoder = Encoder[Color]
    val result = encoder.encode(Color.Red)
    assertEquals(result.asInstanceOf[String], "Red")

  test("Derived Decoder for simple enum should decode from string"):
    val decoder = Decoder[Color]
    val result = decoder.decode("Green".asInstanceOf[js.Any])
    assertEquals(result, Right(Color.Green))

  test("Simple enum should round-trip"):
    val codec = Codec[Color]
    val encoded = codec.encode(Color.Blue)
    val decoded = codec.decode(encoded)
    assertEquals(decoded, Right(Color.Blue))

  test("Decoder for simple enum should fail on unknown value"):
    val decoder = Decoder[Color]
    val result = decoder.decode("Yellow".asInstanceOf[js.Any])
    assert(result.isLeft)
    assert(result.left.exists(_.contains("Unknown enum value")))

  enum Result[+T] derives Codec:
    case Success(value: T)
    case Failure(error: String)

  object Result:
    given [T]: CanEqual[Result[T], Result[T]] = CanEqual.derived

  test("Parameterized ADT should encode with type discriminator"):
    val encoder = Encoder[Result[Int]]
    val success = Result.Success(42)
    val result = encoder.encode(success)
    val obj = result.asInstanceOf[js.Dictionary[js.Any]]
    assertEquals(obj("$type").asInstanceOf[String], "Success")
    assert(obj.contains("$value"))

  test("Parameterized ADT should decode with type discriminator"):
    val decoder = Decoder[Result[Int]]
    val obj = js.Dictionary[js.Any](
      "$type" -> "Failure",
      "$value" -> js.Dictionary[js.Any]("error" -> "Something went wrong")
    )
    val result = decoder.decode(obj.asInstanceOf[js.Any])
    assertEquals(result, Right(Result.Failure("Something went wrong")))

  test("Parameterized ADT should round-trip"):
    val codec = Codec[Result[String]]
    val original = Result.Success("hello")
    val encoded = codec.encode(original)
    val decoded = codec.decode(encoded)
    assertEquals(decoded, Right(original))

  // ====================
  // Edge Cases
  // ====================

  test("Empty list should encode/decode correctly"):
    val encoder = Encoder[List[Int]]
    val decoder = Decoder[List[Int]]
    val encoded = encoder.encode(List.empty)
    val decoded = decoder.decode(encoded)
    assertEquals(decoded, Right(List.empty))

  test("Empty map should encode/decode correctly"):
    val encoder = Encoder[Map[String, Int]]
    val decoder = Decoder[Map[String, Int]]
    val encoded = encoder.encode(Map.empty)
    val decoded = decoder.decode(encoded)
    assertEquals(decoded, Right(Map.empty))

  case class Empty()

  object Empty:
    given CanEqual[Empty, Empty] = CanEqual.derived
    given Codec[Empty] = Codec.derived

  test("Empty case class should encode/decode"):
    val encoder = Encoder[Empty]
    val decoder = Decoder[Empty]
    val encoded = encoder.encode(Empty())
    val decoded = decoder.decode(encoded)
    assertEquals(decoded, Right(Empty()))

  case class WithOption(value: Option[String])

  object WithOption:
    given CanEqual[WithOption, WithOption] = CanEqual.derived
    given Codec[WithOption] = Codec.derived

  test("Case class with None field should handle missing JS field"):
    val decoder = Decoder[WithOption]
    val obj = js.Dictionary[js.Any]() // Empty object
    val result = decoder.decode(obj.asInstanceOf[js.Any])
    assertEquals(result, Right(WithOption(None)))

  test("Case class with Some field should decode present field"):
    val decoder = Decoder[WithOption]
    val obj = js.Dictionary[js.Any]("value" -> "present")
    val result = decoder.decode(obj.asInstanceOf[js.Any])
    assertEquals(result, Right(WithOption(Some("present"))))

  // ====================
  // Codec Bidirectionality
  // ====================

  case class Complex(
    id: Int,
    name: String,
    tags: List[String],
    metadata: Option[Map[String, Double]],
    nested: Option[Person]
  )

  object Complex:
    given CanEqual[Complex, Complex] = CanEqual.derived
    given Codec[Complex] = Codec.derived

  test("Complex structure should round-trip correctly"):
    val codec = Codec[Complex]
    val original = Complex(
      id = 123,
      name = "Test",
      tags = List("a", "b", "c"),
      metadata = Some(Map("x" -> 1.5, "y" -> 2.5)),
      nested = Some(Person("Nested", 40))
    )

    val encoded = codec.encode(original)
    val decoded = codec.decode(encoded)
    assertEquals(decoded, Right(original))

  test("Complex structure with Nones should round-trip"):
    val codec = Codec[Complex]
    val original = Complex(
      id = 456,
      name = "Minimal",
      tags = List.empty,
      metadata = None,
      nested = None
    )

    val encoded = codec.encode(original)
    val decoded = codec.decode(encoded)
    assertEquals(decoded, Right(original))

  // ====================
  // Extension Methods
  // ====================

  test("toJS extension should work"):
    import Encoder.toJS
    val result = "hello".toJS
    assertEquals(result.asInstanceOf[String], "hello")

  test("fromJS extension should work"):
    import Decoder.fromJS
    val jsValue = 42.asInstanceOf[js.Any]
    val result = jsValue.fromJS[Int]
    assertEquals(result, Right(42))

  // ====================
  // Error Messages
  // ====================

  test("Decoder errors should be descriptive"):
    val decoder = Decoder[Person]
    val result = decoder.decode(123.asInstanceOf[js.Any])
    assert(result.isLeft)
    val error = result.left.getOrElse("")
    assert(error.contains("Expected object"))
    assert(error.contains("number"))

  test("Nested decode errors should include path"):
    val decoder = Decoder[Employee]
    val obj = js.Dictionary[js.Any](
      "name" -> "Test",
      "address" -> js.Dictionary[js.Any]("street" -> "123 Main"), // missing city
      "projects" -> js.Array()
    )
    val result = decoder.decode(obj.asInstanceOf[js.Any])
    assert(result.isLeft)
    val error = result.left.getOrElse("")
    assert(error.contains("address"))
    assert(error.contains("city"))
end CodecSuite
