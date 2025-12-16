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

import scala.scalajs.js

import munit.FunSuite

class EventTargetSuite extends FunSuite:
// scalafix:off

  test("EventTarget.Any converts to Any kind"):
    val jsValue = EventTarget.Any.toJS.asInstanceOf[js.Dynamic]
    assertEquals(jsValue.kind.asInstanceOf[String], "Any")

  test("EventTarget.AnyLabel carries label"):
    val jsValue = EventTarget.AnyLabel("main").toJS.asInstanceOf[js.Dynamic]
    assertEquals(jsValue.kind.asInstanceOf[String], "AnyLabel")
    assertEquals(jsValue.label.asInstanceOf[String], "main")

  test("EventTarget.Window encodes label"):
    val jsValue = EventTarget.Window("primary").toJS.asInstanceOf[js.Dynamic]
    assertEquals(jsValue.kind.asInstanceOf[String], "Window")
    assertEquals(jsValue.label.asInstanceOf[String], "primary")

  test("TauriEvent exposes raw value"):
    assertEquals(TauriEvent.WindowBlur.value, "tauri://blur")
end EventTargetSuite

// scalafix:on
