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

import scala.annotation.nowarn
import scala.compiletime.constValue
import scala.compiletime.erasedValue
import scala.compiletime.summonInline
import scala.deriving.*
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

// scalafix:off DisableSyntax.asInstanceOf, DisableSyntax.null, DisableSyntax.while, DisableSyntax.throw, DisableSyntax.var

// ===========================
// Shared Derivation Helpers
// ===========================

// Check if ADT children have fields (are case classes, not singletons)
private transparent inline def hasFieldsInChildren[T <: Tuple]: Boolean =
  inline erasedValue[T] match
    case _: EmptyTuple => false
    case _: (t *: ts)  =>
      inline summonInline[Mirror.Of[t]] match
        case s: Mirror.Singleton    => hasFieldsInChildren[ts] // Singleton, check next
        case p: Mirror.ProductOf[t] =>
          // Check if product has fields
          inline erasedValue[p.MirroredElemTypes] match
            case _: EmptyTuple => hasFieldsInChildren[ts] // No-arg product, check next
            case _             => true // Has fields

// Encode ADT child by ordinal (recursively derive encoders inline)
private inline def encodeByOrdinal[A, T <: Tuple](value: A, ordinal: Int): js.Any =
  inline erasedValue[T] match
    case _: EmptyTuple => js.undefined
    case _: (t *: ts)  =>
      if ordinal == 0 then Encoder.derived[t](using summonInline[Mirror.Of[t]]).encode(value.asInstanceOf[t])
      else encodeByOrdinal[A, ts](value, ordinal - 1)

// Decode ADT child by ordinal (recursively derive decoders inline)
private inline def decodeByOrdinal[A, T <: Tuple](value: js.Any, ordinal: Int): Either[String, A] =
  inline erasedValue[T] match
    case _: EmptyTuple => Left("Invalid ordinal")
    case _: (t *: ts)  =>
      if ordinal == 0 then Decoder.derived[t](using summonInline[Mirror.Of[t]]).decode(value).asInstanceOf[Either[String, A]]
      else decodeByOrdinal[A, ts](value, ordinal - 1)

// Try decoding with each ADT variant decoder (for non-discriminated ADTs)
private inline def tryDecodeVariants[A, T <: Tuple](value: js.Any): Either[String, A] =
  inline erasedValue[T] match
    case _: EmptyTuple => Left("No variant matched")
    case _: (t *: ts)  =>
      Decoder.derived[t](using summonInline[Mirror.Of[t]]).decode(value) match
        case Right(result) => Right(result.asInstanceOf[A])
        case Left(_)       => tryDecodeVariants[A, ts](value)

// Construct enum singleton from ordinal
private inline def ordinalToValue[A, T <: Tuple](ordinal: Int): A =
  inline erasedValue[T] match
    case _: EmptyTuple => throw new IllegalArgumentException(s"Invalid ordinal: $ordinal")
    case _: (t *: ts)  =>
      if ordinal == 0 then summonInline[Mirror.ProductOf[t]].fromProduct(EmptyTuple).asInstanceOf[A]
      else ordinalToValue[A, ts](ordinal - 1)

/** Encodes Scala values to JavaScript values for Tauri interoperability. */
trait Encoder[A]:
  /** Encode a Scala value to a JavaScript value. */
  def encode(value: A): js.Any

object Encoder:

  /** Summon an encoder instance. */
  inline def apply[A](using enc: Encoder[A]): Encoder[A] = enc

  // Primitive encoders
  given Encoder[String] with
    def encode(value: String): js.Any = value.asInstanceOf[js.Any]

  given Encoder[Int] with
    def encode(value: Int): js.Any = value.asInstanceOf[js.Any]

  given Encoder[Long] with
    def encode(value: Long): js.Any = value.toDouble.asInstanceOf[js.Any]

  given Encoder[Double] with
    def encode(value: Double): js.Any = value.asInstanceOf[js.Any]

  given Encoder[Boolean] with
    def encode(value: Boolean): js.Any = value.asInstanceOf[js.Any]

  given Encoder[Unit] with
    def encode(value: Unit): js.Any = js.undefined

  // Collection encoders
  given [A](using e: Encoder[A]): Encoder[List[A]] with
    def encode(value: List[A]): js.Any =
      value.map(e.encode).toJSArray.asInstanceOf[js.Any]

  given [A](using e: Encoder[A]): Encoder[Vector[A]] with
    def encode(value: Vector[A]): js.Any =
      value.map(e.encode).toJSArray.asInstanceOf[js.Any]

  given [A](using e: Encoder[A]): Encoder[Option[A]] with
    def encode(value: Option[A]): js.Any = value match
      case Some(a) => e.encode(a)
      case None    => js.undefined

  given [K, V](using e: Encoder[V]): Encoder[Map[K, V]] with
    def encode(value: Map[K, V]): js.Any =
      val dict = js.Dictionary.empty[js.Any]
      value.foreach { (k, v) =>
        dict(k.toString) = e.encode(v)
      }
      dict.asInstanceOf[js.Any]

  // Derivation support
  private inline def summonAll[T <: Tuple]: List[Encoder[?]] =
    inline erasedValue[T] match
      case _: EmptyTuple => Nil
      case _: (t *: ts)  => summonInline[Encoder[t]] :: summonAll[ts]

  @nowarn inline def derived[A](using m: Mirror.Of[A]): Encoder[A] =
    inline m match
      case s: Mirror.SumOf[A] =>
        new Encoder[A]: // Intentional inline instantiation for compile-time specialization
          private val isSimpleEnum = !hasFieldsInChildren[m.MirroredElemTypes]
          def encode(value: A): js.Any =
            val ordinal = s.ordinal(value)
            val labels = getFieldLabels[m.MirroredElemLabels]
            if isSimpleEnum then
              // Simple enum with no fields - encode as string
              labels(ordinal).asInstanceOf[js.Any]
            else
              // ADT with case classes - encode with type discriminator
              val encoded = encodeByOrdinal[A, m.MirroredElemTypes](value, ordinal)
              val obj = js.Dictionary.empty[js.Any]
              obj("$type") = labels(ordinal)
              obj("$value") = encoded
              obj.asInstanceOf[js.Any]
          end encode
      case p: Mirror.ProductOf[A] =>
        val encoders = summonAll[m.MirroredElemTypes]
        new Encoder[A]: // Intentional inline instantiation for compile-time specialization
          def encode(value: A): js.Any =
            val product = value.asInstanceOf[Product]
            val labels = getFieldLabels[m.MirroredElemLabels]
            val obj = js.Dictionary.empty[js.Any]
            var i = 0
            while i < encoders.length do
              obj(labels(i)) = encoders(i).asInstanceOf[Encoder[Any]].encode(product.productElement(i))
              i += 1
            obj.asInstanceOf[js.Any]

  private inline def getFieldLabels[T <: Tuple]: List[String] =
    inline erasedValue[T] match
      case _: EmptyTuple => Nil
      case _: (t *: ts)  => constValue[t].asInstanceOf[String] :: getFieldLabels[ts]

  extension [A](value: A) def toJS(using enc: Encoder[A]): js.Any = enc.encode(value)
end Encoder

/** Decodes JavaScript values to Scala values for Tauri interoperability. */
trait Decoder[A]:
  /** Decode a JavaScript value to a Scala value.
    *
    * @return Right with decoded value or Left with error message
    */
  def decode(value: js.Any): Either[String, A]

object Decoder:

  /** Summon a decoder instance. */
  inline def apply[A](using dec: Decoder[A]): Decoder[A] = dec

  // Primitive decoders
  given Decoder[String] with
    def decode(value: js.Any): Either[String, String] =
      if js.typeOf(value) == "string" then Right(value.asInstanceOf[String])
      else Left(s"Expected string, got ${js.typeOf(value)}")

  given Decoder[Int] with
    def decode(value: js.Any): Either[String, Int] =
      if js.typeOf(value) == "number" then
        val num = value.asInstanceOf[Double]
        if num.isValidInt then Right(num.toInt)
        else Left(s"Number $num is not a valid Int")
      else Left(s"Expected number, got ${js.typeOf(value)}")

  given Decoder[Long] with
    def decode(value: js.Any): Either[String, Long] =
      if js.typeOf(value) == "number" then Right(value.asInstanceOf[Double].toLong)
      else Left(s"Expected number, got ${js.typeOf(value)}")

  given Decoder[Double] with
    def decode(value: js.Any): Either[String, Double] =
      if js.typeOf(value) == "number" then Right(value.asInstanceOf[Double])
      else Left(s"Expected number, got ${js.typeOf(value)}")

  given Decoder[Boolean] with
    def decode(value: js.Any): Either[String, Boolean] =
      if js.typeOf(value) == "boolean" then Right(value.asInstanceOf[Boolean])
      else Left(s"Expected boolean, got ${js.typeOf(value)}")

  given Decoder[Unit] with
    def decode(value: js.Any): Either[String, Unit] = Right(())

  // Collection decoders
  given [A](using d: Decoder[A]): Decoder[List[A]] with
    def decode(value: js.Any): Either[String, List[A]] =
      if js.Array.isArray(value.asInstanceOf[js.Object]) then
        val arr = value.asInstanceOf[js.Array[js.Any]]
        val results = scala.collection.mutable.ListBuffer.empty[A]
        var i = 0
        while i < arr.length do
          d.decode(arr(i)) match
            case Right(a)  => results += a
            case Left(err) => return Left(s"Array element $i: $err") // scalafix:ok
          i += 1
        Right(results.toList)
      else Left(s"Expected array, got ${js.typeOf(value)}")
  end given

  given [A](using Decoder[A]): Decoder[Vector[A]] with
    def decode(value: js.Any): Either[String, Vector[A]] =
      Decoder[List[A]].decode(value).map(_.toVector)

  given [A](using d: Decoder[A]): Decoder[Option[A]] with
    def decode(value: js.Any): Either[String, Option[A]] =
      if value == null || js.isUndefined(value) then Right(None)
      else d.decode(value).map(Some(_))

  given [K, V](using d: Decoder[V]): Decoder[Map[K, V]] with
    def decode(value: js.Any): Either[String, Map[K, V]] =
      if js.typeOf(value) == "object" && value != null && !js.Array.isArray(value) then
        val dict = value.asInstanceOf[js.Dictionary[js.Any]]
        dict.keys.foldLeft[Either[String, Map[K, V]]](Right(Map.empty)) { (acc, key) =>
          acc
            .flatMap { map =>
              d.decode(dict(key)).map(v => map + (key.asInstanceOf[K] -> v))
            }
            .left
            .map(err => s"Map value for key '$key': $err")
        }
      else Left(s"Expected object, got ${js.typeOf(value)}")
  end given

  // Derivation support
  private inline def summonAll[T <: Tuple]: List[Decoder[?]] =
    inline erasedValue[T] match
      case _: EmptyTuple => Nil
      case _: (t *: ts)  => summonInline[Decoder[t]] :: summonAll[ts]

  @nowarn inline def derived[A](using m: Mirror.Of[A]): Decoder[A] =
    inline m match
      case s: Mirror.SumOf[A] =>
        new Decoder[A]: // Intentional inline instantiation for compile-time specialization
          private val isSimpleEnum = !hasFieldsInChildren[m.MirroredElemTypes]
          def decode(value: js.Any): Either[String, A] =
            val labels = getFieldLabels[m.MirroredElemLabels]
            if isSimpleEnum then
              // Simple enum with singleton cases - decode from string
              if js.typeOf(value) == "string" then
                val str = value.asInstanceOf[String]
                val idx = labels.indexOf(str)
                if idx >= 0 && idx < labels.length then
                  // Construct enum singleton from ordinal using inline match
                  Right(ordinalToValue[A, m.MirroredElemTypes](idx))
                else Left(s"Unknown enum value: $str, expected one of: ${labels.mkString(", ")}")
              else Left(s"Expected string for enum, got ${js.typeOf(value)}")
            else
              // ADT with case classes - check for type discriminator or try each variant
              if js.typeOf(value) == "object" && value != null then
                val obj = value.asInstanceOf[js.Dictionary[js.Any]]
                obj.get("$type") match
                  case Some(typeName) =>
                    val idx = labels.indexOf(typeName.asInstanceOf[String])
                    if idx >= 0 then decodeByOrdinal[A, m.MirroredElemTypes](obj.getOrElse("$value", js.undefined), idx)
                    else Left(s"Unknown type: $typeName")
                  case None =>
                    // No discriminator, try each decoder
                    tryDecodeVariants[A, m.MirroredElemTypes](value)
              else Left(s"Expected object for ADT, got ${js.typeOf(value)}")
            end if
          end decode
      case p: Mirror.ProductOf[A] =>
        val decoders = summonAll[m.MirroredElemTypes]
        new Decoder[A]: // Intentional inline instantiation for compile-time specialization
          def decode(value: js.Any): Either[String, A] =
            if js.typeOf(value) != "object" || value == null then Left(s"Expected object, got ${js.typeOf(value)}")
            else
              val obj = value.asInstanceOf[js.Dictionary[js.Any]]
              val labels = getFieldLabels[m.MirroredElemLabels]

              labels
                .zip(decoders)
                .foldLeft[Either[String, List[Any]]](Right(Nil)) { case (acc, (label, decoder)) =>
                  acc.flatMap { list =>
                    val fieldValue = obj.get(label).getOrElse(js.undefined)
                    decoder
                      .asInstanceOf[Decoder[Any]]
                      .decode(fieldValue)
                      .map(a => list :+ a)
                      .left
                      .map(err => s"Field '$label': $err")
                  }
                }
                .map(fields => p.fromProduct(Tuple.fromArray(fields.toArray)))
        end new

  private inline def getFieldLabels[T <: Tuple]: List[String] =
    inline erasedValue[T] match
      case _: EmptyTuple => Nil
      case _: (t *: ts)  => constValue[t].asInstanceOf[String] :: getFieldLabels[ts]

  extension (value: js.Any) def fromJS[A](using dec: Decoder[A]): Either[String, A] = dec.decode(value)
end Decoder

/** Combined encoder and decoder.
  *
  * Useful for bidirectional conversions.
  */
trait Codec[A] extends Encoder[A], Decoder[A]

object Codec:

  /** Summon a codec instance. */
  inline def apply[A](using codec: Codec[A]): Codec[A] = codec

  /** Construct a codec from an encoder and decoder. */
  def from[A](using enc: Encoder[A], dec: Decoder[A]): Codec[A] =
    new Codec[A]:
      def encode(value: A): js.Any = enc.encode(value)
      def decode(value: js.Any): Either[String, A] = dec.decode(value)

  // Derive codec from encoder and decoder
  given [A](using enc: Encoder[A], dec: Decoder[A]): Codec[A] = from[A]

  inline def derived[A](using m: Mirror.Of[A]): Codec[A] =
    from[A](using Encoder.derived[A], Decoder.derived[A])
end Codec
