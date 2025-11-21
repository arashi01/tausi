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

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

import scala.scalajs.js

import tausi.api.internal.ChannelMessageJS
import tausi.api.internal.SerializeToIpcKey
import tausi.api.internal.TauriInternalsGlobal

/** Type-safe Channel for streaming data from Rust to the frontend.
  *
  * Channels provide ordered, efficient delivery of data. They are used internally for streaming
  * operations like download progress, child process output, etc.
  *
  * The channel ensures messages are delivered in order even if they arrive out of order from the
  * IPC layer. This mirrors the upstream @tauri-apps/api Channel implementation.
  *
  * @tparam T The type of messages the channel receives
  *
  * @example
  *   {{{
  * import tausi.api.Channel
  *
  * // Create a channel that prints received messages
  * val channel = Channel[String] { message =>
  *   println(s"Received: $message")
  * }
  *
  * // Pass channel to a command that streams data
  * core.invoke[Unit](
  *   "stream_data",
  *   js.Dictionary("channel" -> channel.toIPC)
  * )
  *   }}}
  */
final class Channel[T] private (initialHandler: T => Unit):
  private val messageHandler: AtomicReference[T => Unit] = new AtomicReference(initialHandler)
  private val nextMessageIndex: AtomicInteger = new AtomicInteger(0)
  private val pendingMessages: js.Array[T] = js.Array() // Sparse array for O(1) access
  private val messageEndIndex: AtomicReference[Option[Int]] = new AtomicReference(None)
  private val isCleanedUp: AtomicBoolean = new AtomicBoolean(false)

  /** The callback ID for this channel. This is used internally by Tauri to route messages to the
    * correct channel.
    */
  val id: CallbackId =
    val rawCallback: js.Function1[js.Dynamic, Unit] = (rawMessage: js.Dynamic) =>
      if !isCleanedUp.get() then
        val index = ChannelMessageJS.getIndex(rawMessage)

        if ChannelMessageJS.isEnd(rawMessage) then
          // This is the end marker
          val currentIndex = nextMessageIndex.get()
          if index == currentIndex then cleanup()
          else messageEndIndex.set(Some(index))
        else
          // This is a normal message
          val message = ChannelMessageJS.getMessage[T](rawMessage)
          val currentIndex = nextMessageIndex.get()

          if index == currentIndex then
            // Process this message immediately
            val handler = messageHandler.get()
            handler(message)
            val _ = nextMessageIndex.incrementAndGet()

            // Process any pending messages that are now in order
            // Using sparse array access - O(1) per message
            // scalafix:off
            var shouldContinue = true
            while shouldContinue do
              val nextIdx = nextMessageIndex.get()
              if pendingMessages.hasOwnProperty(nextIdx.toString) then
                val pendingMsg = pendingMessages(nextIdx)
                val currentHandler = messageHandler.get()
                currentHandler(pendingMsg)
                js.special.delete(pendingMessages, nextIdx.toString)
                val _ = nextMessageIndex.incrementAndGet()
              else shouldContinue = false
            // scalafix:on
            // Check if we've reached the end
            val finalIndex = nextMessageIndex.get()
            messageEndIndex.get() match
              case Some(endIdx) if endIdx == finalIndex => cleanup()
              case _                                    => ()
          else
            // Queue this message for later (sparse array like upstream)
            pendingMessages(index) = message
          end if
        end if

    val callbackId = TauriInternalsGlobal.transformCallback(
      rawCallback,
      false // not once - channel receives multiple messages
    )
    CallbackId.unsafe(callbackId)
  end id

  /** Clean up the channel and unregister the callback.
    *
    * After cleanup, the channel will no longer receive messages. Ensures exactly-once cleanup
    * semantics.
    */
  private def cleanup(): Unit =
    if isCleanedUp.compareAndSet(false, true) then TauriInternalsGlobal.unregisterCallback(id.toInt)
end Channel

object Channel:
  /** Enable equality for Channel (by reference) */
  given [T]: CanEqual[Channel[T], Channel[T]] = CanEqual.derived

  /** Create a new channel with a message handler.
    *
    * @param onMessage Function to handle received messages
    * @tparam T The type of messages
    * @return A new Channel instance
    */
  def apply[T](onMessage: T => Unit): Channel[T] =
    new Channel[T](onMessage)

  /** Create a channel with no initial handler.
    *
    * The handler can be set later using [[setOnMessage]] extension.
    *
    * @tparam T The type of messages
    * @return A new Channel instance
    */
  def empty[T]: Channel[T] = new Channel[T](_ => ())

  extension [T](channel: Channel[T])
    /** Update the message handler atomically.
      *
      * @param handler The new message handler function
      */
    def setOnMessage(handler: T => Unit): Unit = channel.messageHandler.set(handler)

    /** Get the current message handler.
      *
      * @return The current handler function
      */
    def onMessage: T => Unit = channel.messageHandler.get()

    /** Serialise this channel for IPC.
      *
      * Returns a special string that Tauri recognises as a channel identifier.
      */
    def toIPC: String = s"__CHANNEL__:${channel.id.toInt}"

    /** Check if this channel has been cleaned up.
      *
      * @return true if closed, false otherwise
      */
    def isClosed: Boolean = channel.isCleanedUp.get()

    /** Manually close the channel.
      *
      * After closing, the channel will no longer receive messages. Idempotent and safe to call
      * multiple times.
      */
    def close(): Unit = channel.cleanup()

    /** Convert a Channel to a js.Any for passing to invoke commands.
      *
      * This creates the proper IPC serialisation format.
      */
    def toJSAny: js.Any =
      val payload = js.Dynamic.literal()
      payload.updateDynamic(SerializeToIpcKey)(channel.toIPC)
      payload.asInstanceOf[js.Any] // scalafix:ok

    /** JSON serialisation hook used by the IPC bridge. */
    def toJSON: String = channel.toIPC
  end extension
end Channel
