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
package tausi.zio

import zio.*

import munit.ZSuite

/** Tests for effect execution extensions.
  *
  * Tests the `runWith` overloads for executing ZIO effects from callback contexts.
  */
class EffectExecutionSpec extends ZSuite:

  given Runtime[Any] = Runtime.default

  // ====================
  // runWith(onResult: Either => Unit) tests
  // ====================

  testZ("runWith(Either) should invoke callback with Right on success"):
    for
      promise <- Promise.make[Nothing, Either[Throwable, Int]]
      effect = ZIO.succeed(42)
      _ = effect.runWith(result =>
            Unsafe.unsafe { implicit unsafe =>
              Runtime.default.unsafe.run(promise.succeed(result).unit)(using Trace.empty, unsafe): Unit
            }
          )
      result <- promise.await
    yield assertEquals(result, Right(42))

  testZ("runWith(Either) should invoke callback with Left on failure"):
    for
      promise <- Promise.make[Nothing, Either[TestError, Int]]
      effect: IO[TestError, Int] = ZIO.fail(TestError("boom"))
      _ = effect.runWith(result =>
            Unsafe.unsafe { implicit unsafe =>
              Runtime.default.unsafe.run(promise.succeed(result).unit)(using Trace.empty, unsafe): Unit
            }
          )
      result <- promise.await
    yield
      assert(result.isLeft)
      assertEquals(result.left.toOption.map(_.message), Some("boom"))

  // ====================
  // runWith(onSuccess, onError) tests
  // ====================

  testZ("runWith(success, error) should invoke onSuccess callback on success"):
    for
      promise <- Promise.make[Nothing, Int]
      effect = ZIO.succeed(42)
      _ = effect.runWith(
            onSuccess = a =>
              Unsafe.unsafe { implicit unsafe =>
                Runtime.default.unsafe.run(promise.succeed(a).unit)(using Trace.empty, unsafe): Unit
              },
            onError = _ => ()
          )
      result <- promise.await
    yield assertEquals(result, 42)

  testZ("runWith(success, error) should invoke onError callback on failure"):
    for
      promise <- Promise.make[Nothing, String]
      effect: IO[TestError, Int] = ZIO.fail(TestError("failure"))
      _ = effect.runWith(
            onSuccess = _ => (),
            onError = e =>
              Unsafe.unsafe { implicit unsafe =>
                Runtime.default.unsafe.run(promise.succeed(e.message).unit)(using Trace.empty, unsafe): Unit
              }
          )
      result <- promise.await
    yield assertEquals(result, "failure")

  // ====================
  // runWithUnsafe() (fire-and-forget) tests
  // ====================

  testZ("runWithUnsafe() should complete successfully for successful effects"):
    for
      ref <- Ref.make(0)
      effect = ref.set(42)
      _ = effect.runWithUnsafe()
      _ <- ZIO.sleep(50.millis) // Allow async execution to complete
      result <- ref.get
    yield assertEquals(result, 42)

  testZ("runWithUnsafe() should not throw on failure (errors silently dropped)"):
    for
      ref <- Ref.make(false)
      effect: IO[TestError, Unit] = ZIO.fail(TestError("ignored")).ensuring(ref.set(true))
      _ = effect.runWithUnsafe()
      _ <- ZIO.sleep(50.millis) // Allow async execution to complete
      // Effect should have run (and failed), triggering ensuring
      executed <- ref.get
    yield assert(executed)

  // ====================
  // Edge cases
  // ====================

  testZ("runWith should handle effects that return Unit"):
    for
      promise <- Promise.make[Nothing, Either[Throwable, Unit]]
      effect = ZIO.unit
      _ = effect.runWith(result =>
            Unsafe.unsafe { implicit unsafe =>
              Runtime.default.unsafe.run(promise.succeed(result).unit)(using Trace.empty, unsafe): Unit
            }
          )
      result <- promise.await
    yield assertEquals(result, Right(()))

  testZ("runWith should handle effects that return complex types"):
    for
      promise <- Promise.make[Nothing, Either[Throwable, List[String]]]
      effect = ZIO.succeed(List("a", "b", "c"))
      _ = effect.runWith(result =>
            Unsafe.unsafe { implicit unsafe =>
              Runtime.default.unsafe.run(promise.succeed(result).unit)(using Trace.empty, unsafe): Unit
            }
          )
      result <- promise.await
    yield assertEquals(result, Right(List("a", "b", "c")))

end EffectExecutionSpec

/** Test error type for effect execution tests. */
final case class TestError(message: String) extends Throwable(message)

object TestError:
  given CanEqual[TestError, TestError] = CanEqual.derived
