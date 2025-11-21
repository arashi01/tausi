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

import munit.FunSuite

class CoreApiSuite extends FunSuite:
  // scalafix:off
  test("core.isTauri should return false in non-browser environment"):
    // In Node.js test environment, window doesn't exist so isTauri returns false
    // In actual Tauri environment, this would return true
    val result = core.isTauri
    assertEquals(result, false)

  test("InvokeOptions should support empty options"):
    val opts = InvokeOptions.empty
    assert(opts.headers.isEmpty)

  test("InvokeOptions should support headers"):
    val opts = InvokeOptions(Map("Authorization" -> "Bearer token"))
    assertEquals(opts.headers.size, 1)
    assertEquals(opts.headers.get("Authorization"), Some("Bearer token"))

  test("InvokeOptions.toJS should convert to JS format"):
    val opts = InvokeOptions(Map("X-Custom" -> "value"))
    val jsOpts = opts.toJS
    // Just ensure it doesn't throw
    assert(jsOpts != null)

  // TODO: Enable with WebDriver testing
  // test("Channel should have unique IDs"):
  //   val ch1 = Channel[String]()
  //   val ch2 = Channel[String]()
  //   assert(ch1.id != ch2.id)
  //
  // test("Channel should track closed state"):
  //   val ch = Channel[String]()
  //   assert(!ch.isClosed)
  //   ch.close()
  //   assert(ch.isClosed)
  //
  // test("Channel.toIPC should format correctly"):
  //   val ch = Channel[String]()
  //   val ipc = ch.toIPC
  //   assert(ipc.startsWith("__CHANNEL__:"))
  //   assert(ipc.contains(ch.id.toInt.toString))
  //
  // test("Channel should allow setting message handler"):
  //   var received: Option[String] = None
  //   val ch = Channel[String]()
  //   ch.setOnMessage { msg => received = Some(msg) }
  //   // We can't test actual message receipt without Tauri runtime,
  //   // but we can verify the setter doesn't throw
  //   assert(ch.onMessage != null)
  //
  // test("isTauri should return false in test environment"):
  //   // In test environment without Tauri, should be false
  //   assertEquals(isTauri, false)
  // scalafix:on
end CoreApiSuite
