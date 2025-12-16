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
package tausi.api.commands

import scala.scalajs.js
import scala.scalajs.js.typedarray.Uint8Array

import tausi.api.Command
import tausi.api.CommandId
import tausi.api.Plugin
import tausi.api.ResourceId
import tausi.api.codec.Codec
import tausi.api.codec.Decoder
import tausi.api.codec.Encoder

/** Image plugin commands.
  *
  * Combines generated commands with manual implementations for commands that require special byte
  * array handling.
  */
object image extends ImageCommandsGenerated:
  given plugin: Plugin[image.type] = Plugin.noop("image")

  /** RGBA pixel data as a byte array */
  opaque type RgbaData = Uint8Array
  object RgbaData:
    def apply(data: Uint8Array): RgbaData = data
    def apply(bytes: Array[Byte]): RgbaData =
      val arr = new Uint8Array(bytes.length)
      bytes.indices.foreach(i => arr(i) = (bytes(i) & 0xff).toShort)
      arr

    extension (data: RgbaData)
      def bytes: Uint8Array = data
      def toArray: Array[Byte] =
        val arr = new Array[Byte](data.length)
        (0 until data.length).foreach(i => arr(i) = data(i).toByte)
        arr

    given Codec[RgbaData] = Codec[Uint8Array].asInstanceOf[Codec[RgbaData]] // scalafix:ok
  end RgbaData

  /** Image dimensions */
  final case class ImageSize(width: Int, height: Int) derives CanEqual, Codec

  /** Create a new image from RGBA pixel data.
    *
    * @example {{{ import tausi.api.commands.ImageCommands.{given, *} import tausi.api.core.invoke
    *
    * val rgba = RgbaData(Array.fill(100 * 100 * 4)(0.toByte)) // 100x100 black image val imageId =
    * invoke(NewImage(rgba, 100, 100)) }}}
    */
  final case class NewImage(
    rgba: RgbaData,
    width: Int,
    height: Int
  ) derives CanEqual

  given newImageCommand: Command[NewImage, ResourceId] = new Command[NewImage, ResourceId]:
    val id = CommandId.unsafe("plugin:image|new")

    given encoder: Encoder[NewImage] = new Encoder[NewImage]:
      import RgbaData.bytes
      def encode(value: NewImage): js.Any =
        js.Dynamic.literal(
          rgba = value.rgba.bytes,
          width = value.width,
          height = value.height
        )

    given decoder: Decoder[ResourceId] = summon[Codec[ResourceId]]

  /** Create a new image from encoded image bytes (PNG, JPEG, etc.).
    *
    * @example {{{ import tausi.api.commands.ImageCommands.{given, *} import tausi.api.core.invoke
    *
    * val pngBytes: Uint8Array = ... // Load PNG file bytes val imageId =
    * invoke(FromBytes(pngBytes)) }}}
    */
  final case class FromBytes(bytes: Uint8Array) derives CanEqual

  given fromBytesCommand: Command[FromBytes, ResourceId] = new Command[FromBytes, ResourceId]:
    val id = CommandId.unsafe("plugin:image|from_bytes")

    given encoder: Encoder[FromBytes] = new Encoder[FromBytes]:
      def encode(value: FromBytes): js.Any =
        js.Dynamic.literal(
          bytes = value.bytes
        )

    given decoder: Decoder[ResourceId] = summon[Codec[ResourceId]]

end image
