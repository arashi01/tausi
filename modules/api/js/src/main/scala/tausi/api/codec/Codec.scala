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
import scala.util.boundary
import scala.util.boundary.break

// scalafix:off DisableSyntax.asInstanceOf, DisableSyntax.null, DisableSyntax.while, DisableSyntax.throw, DisableSyntax.var

// ===========================
// Shared Derivation Helpers
// ===========================

// Get field labels as IArray for O(1) indexed access (computed once at derivation)
private inline def getFieldLabelsArray[T <: Tuple]: IArray[String] =
  IArray.from(getFieldLabelsList[T])

private inline def getFieldLabelsList[T <: Tuple]: List[String] =
  inline erasedValue[T] match
    case _: EmptyTuple => Nil
    case _: (t *: ts)  => constValue[t].asInstanceOf[String] :: getFieldLabelsList[ts]

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

/** Encodes Scala values to JavaScript values for Tauri interoperability.
  *
  * Encoder is a contravariant functor - the type parameter appears in input position.
  * Use `contramap` to adapt an existing encoder to work with a different input type.
  *
  * @example
  *   {{{
  * // Create encoder for opaque type via contramap
  * opaque type UserId = String
  * object UserId:
  *   def apply(id: String): UserId = id
  *   extension (id: UserId) def value: String = id
  *   given Encoder[UserId] = Encoder[String].contramap(_.value)
  *   }}}
  */
trait Encoder[A]:
  self =>

  /** Encode a Scala value to a JavaScript value. */
  def encode(value: A): js.Any

  /** Create a new encoder that transforms input before encoding (contravariant functor).
    *
    * @param f Function to extract the underlying value from B
    * @return A new Encoder for type B that delegates to this encoder
    */
  def contramap[B](f: B => A): Encoder[B] =
    (value: B) => self.encode(f(value))
end Encoder

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

  given Encoder[js.Any] with
    def encode(value: js.Any): js.Any = value

  given Encoder[js.Dynamic] with
    def encode(value: js.Dynamic): js.Any = value.asInstanceOf[js.Any]

  given Encoder[js.Array[js.Dynamic]] with
    def encode(value: js.Array[js.Dynamic]): js.Any = value.asInstanceOf[js.Any]

  given Encoder[js.typedarray.Uint8Array] with
    def encode(value: js.typedarray.Uint8Array): js.Any = value.asInstanceOf[js.Any]

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

  given [A](using e: Encoder[A]): Encoder[js.Array[A]] with
    def encode(value: js.Array[A]): js.Any =
      value.map(e.encode).asInstanceOf[js.Any]

  given [K, V](using e: Encoder[V]): Encoder[Map[K, V]] with
    def encode(value: Map[K, V]): js.Any =
      val dict = js.Dictionary.empty[js.Any]
      value.foreach { (k, v) =>
        dict(k.toString) = e.encode(v)
      }
      dict.asInstanceOf[js.Any]

  // Derivation support - returns IArray for O(1) indexed access
  private inline def summonAllArray[T <: Tuple]: IArray[Encoder[?]] =
    IArray.from(summonAllList[T])

  private inline def summonAllList[T <: Tuple]: List[Encoder[?]] =
    inline erasedValue[T] match
      case _: EmptyTuple => Nil
      case _: (t *: ts)  => summonInline[Encoder[t]] :: summonAllList[ts]

  @nowarn inline def derived[A](using m: Mirror.Of[A]): Encoder[A] =
    inline m match
      case s: Mirror.SumOf[A] =>
        // Hoist labels to derivation time (outside encode method)
        val labels = getFieldLabelsArray[m.MirroredElemLabels]
        val isSimpleEnum = !hasFieldsInChildren[m.MirroredElemTypes]
        new Encoder[A]: // Intentional inline instantiation for compile-time specialization
          def encode(value: A): js.Any =
            val ordinal = s.ordinal(value)
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
        end new
      case p: Mirror.ProductOf[A] =>
        // Hoist labels and encoders to derivation time (outside encode method)
        val labels = getFieldLabelsArray[m.MirroredElemLabels]
        val encoders = summonAllArray[m.MirroredElemTypes]
        new Encoder[A]: // Intentional inline instantiation for compile-time specialization
          def encode(value: A): js.Any =
            val product = value.asInstanceOf[Product]
            val obj = js.Dictionary.empty[js.Any]
            var i = 0
            while i < encoders.length do
              obj(labels(i)) = encoders(i).asInstanceOf[Encoder[Any]].encode(product.productElement(i))
              i += 1
            obj.asInstanceOf[js.Any]

  extension [A](value: A) def toJS(using enc: Encoder[A]): js.Any = enc.encode(value)
end Encoder

/** Decodes JavaScript values to Scala values for Tauri interoperability.
  *
  * Decoder is a covariant functor - the type parameter appears in output position.
  * Use `map` for infallible transformations and `emap` for fallible ones.
  *
  * @example
  *   {{{
  * // Create decoder for opaque type via map
  * opaque type UserId = String
  * object UserId:
  *   def apply(id: String): UserId = id
  *   given Decoder[UserId] = Decoder[String].map(UserId.apply)
  *
  * // Create decoder with validation via emap
  * opaque type PositiveInt = Int
  * object PositiveInt:
  *   def from(n: Int): Either[String, PositiveInt] =
  *     if n > 0 then Right(n) else Left(s"Expected positive, got $n")
  *   given Decoder[PositiveInt] = Decoder[Int].emap(PositiveInt.from)
  *   }}}
  */
trait Decoder[A]:
  self =>

  /** Decode a JavaScript value to a Scala value.
    *
    * @return Right with decoded value or Left with error message
    */
  def decode(value: js.Any): Either[String, A]

  /** Create a new decoder that transforms output after decoding (covariant functor).
    *
    * Use this for infallible transformations where the conversion always succeeds.
    *
    * @param f Function to transform the decoded value
    * @return A new Decoder for type B
    */
  def map[B](f: A => B): Decoder[B] =
    (value: js.Any) => self.decode(value).map(f)

  /** Create a new decoder with fallible transformation (effectful map).
    *
    * Use this when the transformation may fail with a validation error.
    * The error message from `f` will be used as the decode error.
    *
    * @param f Function to validate and transform the decoded value
    * @return A new Decoder for type B
    */
  def emap[B](f: A => Either[String, B]): Decoder[B] =
    (value: js.Any) => self.decode(value).flatMap(f)
end Decoder

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

  given Decoder[js.Any] with
    def decode(value: js.Any): Either[String, js.Any] =
      Right(value)

  given Decoder[js.Dynamic] with
    def decode(value: js.Any): Either[String, js.Dynamic] =
      Right(value.asInstanceOf[js.Dynamic])

  given Decoder[js.Array[js.Dynamic]] with
    def decode(value: js.Any): Either[String, js.Array[js.Dynamic]] =
      Right(value.asInstanceOf[js.Array[js.Dynamic]])

  given Decoder[js.typedarray.Uint8Array] with
    def decode(value: js.Any): Either[String, js.typedarray.Uint8Array] =
      Right(value.asInstanceOf[js.typedarray.Uint8Array])

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

  given [A](using d: Decoder[A]): Decoder[js.Array[A]] with
    def decode(value: js.Any): Either[String, js.Array[A]] =
      if js.Array.isArray(value.asInstanceOf[js.Object]) then
        val arr = value.asInstanceOf[js.Array[js.Any]]
        val results = arr.map(d.decode)
        val errors = results.zipWithIndex.collect { case (Left(err), idx) =>
          s"[$idx]: $err"
        }
        if errors.isEmpty then Right(results.collect { case Right(v) => v }.asInstanceOf[js.Array[A]])
        else Left(s"Array decoding errors: ${errors.mkString(", ")}")
      else Left(s"Expected array, got ${js.typeOf(value)}")

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

  // Derivation support - returns IArray for O(1) indexed access
  private inline def summonAllArray[T <: Tuple]: IArray[Decoder[?]] =
    IArray.from(summonAllList[T])

  private inline def summonAllList[T <: Tuple]: List[Decoder[?]] =
    inline erasedValue[T] match
      case _: EmptyTuple => Nil
      case _: (t *: ts)  => summonInline[Decoder[t]] :: summonAllList[ts]

  @nowarn inline def derived[A](using m: Mirror.Of[A]): Decoder[A] =
    inline m match
      case s: Mirror.SumOf[A] =>
        // Hoist labels to derivation time (outside decode method)
        val labels = getFieldLabelsArray[m.MirroredElemLabels]
        val isSimpleEnum = !hasFieldsInChildren[m.MirroredElemTypes]
        new Decoder[A]: // Intentional inline instantiation for compile-time specialization
          def decode(value: js.Any): Either[String, A] =
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
        end new
      case p: Mirror.ProductOf[A] =>
        // Hoist labels and decoders to derivation time (outside decode method)
        val labels = getFieldLabelsArray[m.MirroredElemLabels]
        val decoders = summonAllArray[m.MirroredElemTypes]
        val fieldCount = labels.length
        new Decoder[A]: // Intentional inline instantiation for compile-time specialization
          def decode(value: js.Any): Either[String, A] =
            if js.typeOf(value) != "object" || value == null then Left(s"Expected object, got ${js.typeOf(value)}")
            else
              val obj = value.asInstanceOf[js.Dictionary[js.Any]]
              // Use boundary/break for structured early exit with indexed iteration
              boundary:
                val arr = new Array[Any](fieldCount)
                var i = 0
                while i < fieldCount do
                  val label = labels(i)
                  val fieldValue = obj.get(label).getOrElse(js.undefined)
                  decoders(i).asInstanceOf[Decoder[Any]].decode(fieldValue) match
                    case Right(v) => arr(i) = v
                    case Left(e)  => break(Left(s"Field '$label': $e"))
                  i += 1
                Right(p.fromProduct(Tuple.fromArray(arr)))
        end new

  extension (value: js.Any) def fromJS[A](using dec: Decoder[A]): Either[String, A] = dec.decode(value)
end Decoder

/** Combined encoder and decoder for bidirectional JavaScript/Scala conversions.
  *
  * Codec is an invariant functor - the type parameter appears in both input and output positions.
  * Use `imap` for bidirectional transformations and `iemap` when decoding may fail.
  *
  * @example
  *   {{{
  * // Simple opaque type wrapping
  * opaque type UserId = String
  * object UserId:
  *   def apply(id: String): UserId = id
  *   extension (id: UserId) def value: String = id
  *   given Codec[UserId] = Codec[String].imap(UserId.apply)(_.value)
  *
  * // Validated opaque type with decode-time validation
  * opaque type PositiveInt = Int
  * object PositiveInt:
  *   def from(n: Int): Either[String, PositiveInt] =
  *     if n > 0 then Right(n) else Left(s"Expected positive, got $n")
  *   def unsafe(n: Int): PositiveInt = n
  *   extension (n: PositiveInt) def value: Int = n
  *   given Codec[PositiveInt] = Codec[Int].iemap(PositiveInt.from)(_.value)
  *   }}}
  */
trait Codec[A] extends Encoder[A], Decoder[A]:
  self =>

  /** Bidirectional transformation for invariant mapping.
    *
    * Use this for opaque types and other simple wrappers where both
    * conversion directions are infallible.
    *
    * @param f Function to construct B from A (used in decoding)
    * @param g Function to extract A from B (used in encoding)
    * @return A new Codec for type B
    */
  def imap[B](f: A => B)(g: B => A): Codec[B] =
    new Codec[B]:
      def encode(value: B): js.Any = self.encode(g(value))
      def decode(value: js.Any): Either[String, B] = self.decode(value).map(f)

  /** Bidirectional transformation with fallible decoding.
    *
    * Use this for validated opaque types where the conversion from the
    * underlying type may fail validation.
    *
    * @param f Function to validate and construct B from A (used in decoding)
    * @param g Function to extract A from B (used in encoding)
    * @return A new Codec for type B
    */
  def iemap[B](f: A => Either[String, B])(g: B => A): Codec[B] =
    new Codec[B]:
      def encode(value: B): js.Any = self.encode(g(value))
      def decode(value: js.Any): Either[String, B] = self.decode(value).flatMap(f)
end Codec

object Codec:

  /** Summon a codec instance. */
  inline def apply[A](using codec: Codec[A]): Codec[A] = codec

  /** Construct a codec from an encoder and decoder. */
  def from[A](using enc: Encoder[A], dec: Decoder[A]): Codec[A] =
    new Codec[A]:
      def encode(value: A): js.Any = enc.encode(value)
      def decode(value: js.Any): Either[String, A] = dec.decode(value)

  // Automatically derive codec from separate encoder and decoder
  given [A](using enc: Encoder[A], dec: Decoder[A]): Codec[A] = from[A]

  inline def derived[A](using m: Mirror.Of[A]): Codec[A] =
    from[A](using Encoder.derived[A], Decoder.derived[A])

  // Special codecs for js.Dynamic types (passthrough)
  given Codec[js.Dynamic] = from(using
    new Encoder[js.Dynamic]:
      def encode(value: js.Dynamic): js.Any = value.asInstanceOf[js.Any]
    ,
    new Decoder[js.Dynamic]:
      def decode(value: js.Any): Either[String, js.Dynamic] = Right(value.asInstanceOf[js.Dynamic])
  )

  given Codec[js.Array[js.Dynamic]] = from(using
    new Encoder[js.Array[js.Dynamic]]:
      def encode(value: js.Array[js.Dynamic]): js.Any = value.asInstanceOf[js.Any]
    ,
    new Decoder[js.Array[js.Dynamic]]:
      def decode(value: js.Any): Either[String, js.Array[js.Dynamic]] = Right(value.asInstanceOf[js.Array[js.Dynamic]])
  )
end Codec
