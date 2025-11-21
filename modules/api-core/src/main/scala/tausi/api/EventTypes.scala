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

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.scalajs.js

/** Data describing a Tauri event emitted from the backend. */
final case class EventMessage[T](name: String, id: EventId, payload: T)

object EventMessage:
  given [T]: CanEqual[EventMessage[T], EventMessage[T]] = CanEqual.derived

/** Opaque identifier assigned to event listeners. */
opaque type EventId = Int

object EventId:
  def unsafe(value: Int): EventId = value
  extension (id: EventId) def toInt: Int = id
  given CanEqual[EventId, EventId] = CanEqual.derived

/** Event targets recognised by Tauri. */
enum EventTarget:
  case Any
  case AnyLabel(label: String)
  case App
  case Window(label: String)
  case Webview(label: String)
  case WebviewWindow(label: String)

object EventTarget:
  given CanEqual[EventTarget, EventTarget] = CanEqual.derived

  extension (target: EventTarget)
    private[api] def toJS: js.Any = target match
      case EventTarget.Any =>
        js.Dynamic.literal("kind" -> "Any")
      case EventTarget.AnyLabel(label) =>
        js.Dynamic.literal("kind" -> "AnyLabel", "label" -> label)
      case EventTarget.App =>
        js.Dynamic.literal("kind" -> "App")
      case EventTarget.Window(label) =>
        js.Dynamic.literal("kind" -> "Window", "label" -> label)
      case EventTarget.Webview(label) =>
        js.Dynamic.literal("kind" -> "Webview", "label" -> label)
      case EventTarget.WebviewWindow(label) =>
        js.Dynamic.literal("kind" -> "WebviewWindow", "label" -> label)
  end extension
end EventTarget

/** Options when registering listeners. */
final case class EventOptions(target: EventTarget)

object EventOptions:
  val default: EventOptions = EventOptions(EventTarget.Any)
  given CanEqual[EventOptions, EventOptions] = CanEqual.derived

  def forTarget(target: EventTarget): EventOptions = EventOptions(target)

/** Handle returned after registering an event listener. */
final case class EventHandle(event: String, eventId: EventId, callbackId: CallbackId)

object EventHandle:
  given CanEqual[EventHandle, EventHandle] = CanEqual.derived

  extension (handle: EventHandle)
    def unlisten()(using ExecutionContext): Future[Unit] =
      tausi.api.event.unlisten(handle)

/** Tauri-provided event constants. */
enum TauriEvent(val value: String):
  case WindowResized extends TauriEvent("tauri://resize")
  case WindowMoved extends TauriEvent("tauri://move")
  case WindowCloseRequested extends TauriEvent("tauri://close-requested")
  case WindowDestroyed extends TauriEvent("tauri://destroyed")
  case WindowFocus extends TauriEvent("tauri://focus")
  case WindowBlur extends TauriEvent("tauri://blur")
  case WindowScaleFactorChanged extends TauriEvent("tauri://scale-change")
  case WindowThemeChanged extends TauriEvent("tauri://theme-changed")
  case WindowCreated extends TauriEvent("tauri://window-created")
  case WebviewCreated extends TauriEvent("tauri://webview-created")
  case DragEnter extends TauriEvent("tauri://drag-enter")
  case DragOver extends TauriEvent("tauri://drag-over")
  case DragDrop extends TauriEvent("tauri://drag-drop")
  case DragLeave extends TauriEvent("tauri://drag-leave")
end TauriEvent

object TauriEvent:
  given CanEqual[TauriEvent, TauriEvent] = CanEqual.derived
