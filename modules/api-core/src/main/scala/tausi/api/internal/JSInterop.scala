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
package internal

import scala.scalajs.js
import scala.scalajs.js.annotation.*
import scala.util.Try

/** Key used by Tauri to identify IPC serialisation payloads. */
private[api] inline val SerializeToIpcKey = "__TAURI_TO_IPC_KEY__"

/** Native JS facade for window.__TAURI_INTERNALS__. Pure JS interop - no Scala behaviour. All
  * methods are native JS calls.
  */
@js.native
private[api] trait TauriInternals extends js.Object:
  /** Invoke a Tauri command. */
  def invoke[T](
    cmd: String,
    args: js.Any,
    options: js.UndefOr[InvokeOptionsJS]
  ): js.Promise[T] = js.native

  /** Register a callback and return its ID. */
  def transformCallback[T](
    callback: js.UndefOr[js.Function1[T, Unit]],
    once: Boolean
  ): Int = js.native

  /** Unregister a callback by ID. */
  def unregisterCallback(id: Int): Unit = js.native

  /** Convert a file path to a URL that can be loaded by the webview. */
  def convertFileSrc(filePath: String, protocol: String): String = js.native
end TauriInternals

/** Accessor for window.__TAURI_INTERNALS__.
  *
  * Pure reference to global object - no behaviour.
  */
@js.native
@JSGlobal("window.__TAURI_INTERNALS__")
private[api] object TauriInternalsGlobal extends TauriInternals

/** Check if running in Tauri environment.
  *
  * Mirrors upstream implementation: `!!((globalThis as any) || window).isTauri` Returns false in
  * Node.js (undefined coerced to false) or if window doesn't exist. Returns true only when
  * window.isTauri is explicitly true.
  */
private[api] lazy val isTauriGlobal: Boolean = Try {
  val windowRef = js.Dynamic.global.window.asInstanceOf[js.UndefOr[js.Dynamic]]
  windowRef.toOption.exists(_.selectDynamic("isTauri").asInstanceOf[js.UndefOr[Boolean]].getOrElse(false))
}.getOrElse(false) // scalafix:ok

/** JS trait for InvokeOptions. */
trait InvokeOptionsJS extends js.Object:
  val headers: js.UndefOr[js.Dictionary[String]]

object InvokeOptionsJS:
  /** Create InvokeOptionsJS with headers.
    *
    * @param headers The headers dictionary
    * @return InvokeOptionsJS instance
    */
  def apply(headers: js.Dictionary[String]): InvokeOptionsJS =
    js.Dynamic.literal(headers = headers).asInstanceOf[InvokeOptionsJS]

  /** Create empty InvokeOptionsJS with no headers.
    *
    * @return InvokeOptionsJS instance with no headers
    */
  def empty: InvokeOptionsJS =
    js.Dynamic.literal().asInstanceOf[InvokeOptionsJS] // scalafix:ok
end InvokeOptionsJS

/** JS trait for raw channel messages.
  *
  * The Tauri channel system sends messages in one of two forms:
  *   - Normal message: { message: T, index: number }
  *   - End marker: { end: true, index: number }
  *
  * Pure data structure - no methods.
  */
private[api] trait ChannelMessageJS[T] extends js.Object

private[api] object ChannelMessageJS:
  /** Check if this is an end marker message. */
  def isEnd(msg: js.Dynamic): Boolean =
    js.typeOf(msg.selectDynamic("end")) != "undefined"

  /** Get the message index. */
  def getIndex(msg: js.Dynamic): Int =
    msg.selectDynamic("index").asInstanceOf[Int]

  /** Get the message payload (only valid for non-end messages). */
  def getMessage[T](msg: js.Dynamic): T =
    msg.selectDynamic("message").asInstanceOf[T] // scalafix:ok
end ChannelMessageJS

/** Facade for window.__TAURI_EVENT_PLUGIN_INTERNALS__.
  *
  * The object is only defined when the event plugin initialises successfully. Access must therefore
  * be guarded through [[EventPluginInternalsBridge]].
  */
@js.native
private trait EventPluginInternals extends js.Object:
  def unregisterListener(event: String, eventId: Int): Unit = js.native

/** Safe bridge to the optional event plugin internals global. */
private[api] object EventPluginInternalsBridge:
  private def lookup: Option[EventPluginInternals] =
    val maybeInternals =
      js.Dynamic.global.selectDynamic("__TAURI_EVENT_PLUGIN_INTERNALS__")
    if js.isUndefined(maybeInternals) then None
    else Option(maybeInternals.asInstanceOf[js.Any | Null]).map(_.asInstanceOf[EventPluginInternals]) // scalafix:ok

  /** Attempt to unregister a listener via the event plugin internals.
    *
    * @return true if the listener was unregistered via the plugin internals, false otherwise
    */
  def unregisterListener(event: String, eventId: Int): Boolean =
    lookup match
      case Some(internals) =>
        internals.unregisterListener(event, eventId)
        true
      case None => false
end EventPluginInternalsBridge
