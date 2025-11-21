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

/** Listener for plugin events.
  *
  * Plugin listeners allow subscribing to events emitted by Tauri plugins. Each listener must be
  * explicitly unregistered when no longer needed.
  *
  * Pure data - no methods. All behaviour in companion extensions.
  *
  * @param plugin The plugin identifier
  * @param event The event name
  * @param channelId The underlying channel ID
  */
final case class PluginListener(
  plugin: String,
  event: String,
  channelId: CallbackId
)

object PluginListener:
  /** Enable equality comparison for PluginListener */
  given CanEqual[PluginListener, PluginListener] = CanEqual.derived

  extension (listener: PluginListener)
    /** Unregister this listener.
      *
      * After unregistration, the listener will no longer receive events. This should be called when
      * the listener is no longer needed to prevent memory leaks.
      *
      * @param ec Execution context for async operations
      * @return Future containing Either a TauriError or Unit
      */
    def unregister()(using ec: ExecutionContext): Future[Either[TauriError, Unit]] =
      // Import core in method to avoid circular dependency
      tausi.api.core.invoke[Unit](
        s"plugin:${listener.plugin}|remove_listener",
        js.Dictionary("event" -> listener.event, "channelId" -> listener.channelId.toInt)
      )
  end extension
end PluginListener
