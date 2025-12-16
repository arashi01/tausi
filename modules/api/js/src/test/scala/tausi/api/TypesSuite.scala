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

class TauriErrorSuite extends FunSuite:

  test("TauriError hierarchy should be sealed"):
    val error: TauriError = TauriError.GenericError("test")
    assert(error.isInstanceOf[TauriError]) // scalafix:ok

  test("InvokeError should contain command information"):
    val error = TauriError.InvokeError("my-command", "failed", None)
    assertEquals(error.command, "my-command")
    assertEquals(error.message, "failed")
    assertEquals(error.cause, None)

  test("PluginError should contain plugin information"):
    val error = TauriError.PluginError("my-plugin", "initialization failed", None)
    assertEquals(error.plugin, "my-plugin")
    assertEquals(error.message, "initialization failed")

  test("ResourceError should contain resource ID"):
    val rid = ResourceId.unsafe(42)
    val error = TauriError.ResourceError.apply(rid, "cleanup failed", None)
    assertEquals(error.resourceId, rid)
    assertEquals(error.message, "cleanup failed")

  test("ConversionError should contain file path and protocol"):
    val error = TauriError.ConversionError("/path/to/file", "asset", "invalid path", None)
    assertEquals(error.filePath, "/path/to/file")
    assertEquals(error.protocol, "asset")

  test("TauriNotAvailableError should have message"):
    val error = TauriError.TauriNotAvailableError.apply("Tauri runtime is not available")
    assert(error.message.contains("Tauri runtime is not available"))

  test("fromThrowable should wrap non-TauriError"):
    val ex = new RuntimeException("oops")
    val error = TauriError.fromThrowable(ex)
    assert(error.isInstanceOf[TauriError.GenericError]) // scalafix:ok
    assertEquals(error.cause, Some(ex))

  test("fromThrowable should preserve TauriError"):
    val original = TauriError.GenericError("test", None)
    val result = TauriError.fromThrowable(original)
    assertEquals(result, original)
end TauriErrorSuite

class TypesSuite extends FunSuite:

  test("CallbackId should wrap integer"):
    val id = CallbackId.unsafe(42)
    assertEquals(id.toInt, 42)

  test("CallbackId show should format correctly"):
    val id = CallbackId.unsafe(123)
    assertEquals(id.show, "CallbackId(123)")

  test("ResourceId should wrap integer"):
    val id = ResourceId.unsafe(99)
    assertEquals(id.toInt, 99)

  test("ResourceId show should format correctly"):
    val id = ResourceId.unsafe(456)
    assertEquals(id.show, "ResourceId(456)")

end TypesSuite
