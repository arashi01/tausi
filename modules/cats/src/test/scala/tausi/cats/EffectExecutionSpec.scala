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
package tausi.cats

import scala.concurrent.duration.*

import cats.effect.*
import cats.effect.std.Dispatcher

import munit.CatsEffectSuite

/** Tests for effect execution extensions.
  *
  * Tests the `runWith` overloads for executing Cats Effect IO from callback contexts.
  */
class EffectExecutionSpec extends CatsEffectSuite:

  // Fixture providing a Dispatcher for all tests
  val dispatcherFixture: SyncIO[FunFixture[Dispatcher[IO]]] =
    ResourceFunFixture(Dispatcher.parallel[IO])

  // ====================
  // runWith(onResult: Either => Unit) tests
  // ====================

  dispatcherFixture.test("runWith(Either) should invoke callback with Right on success"): dispatcher =>
    given Dispatcher[IO] = dispatcher
    for
      deferred <- Deferred[IO, Either[Throwable, Int]]
      effect = IO.pure(42)
      _ = effect.runWith(result => dispatcher.unsafeRunAndForget(deferred.complete(result)))
      result <- deferred.get.timeout(1.second)
    yield assertEquals(result, Right(42))

  dispatcherFixture.test("runWith(Either) should invoke callback with Left on failure"): dispatcher =>
    given Dispatcher[IO] = dispatcher
    for
      deferred <- Deferred[IO, Either[Throwable, Int]]
      effect: IO[Int] = IO.raiseError(TestException("boom"))
      _ = effect.runWith(result => dispatcher.unsafeRunAndForget(deferred.complete(result)))
      result <- deferred.get.timeout(1.second)
    yield
      assert(result.isLeft)
      assertEquals(result.left.toOption.map(_.getMessage), Some("boom"))

  // ====================
  // runWith(onSuccess, onError) tests
  // ====================

  dispatcherFixture.test("runWith(success, error) should invoke onSuccess callback on success"): dispatcher =>
    given Dispatcher[IO] = dispatcher
    for
      deferred <- Deferred[IO, Int]
      effect = IO.pure(42)
      _ = effect.runWith(
            onSuccess = a => dispatcher.unsafeRunAndForget(deferred.complete(a)),
            onError = _ => ()
          )
      result <- deferred.get.timeout(1.second)
    yield assertEquals(result, 42)

  dispatcherFixture.test("runWith(success, error) should invoke onError callback on failure"): dispatcher =>
    given Dispatcher[IO] = dispatcher
    for
      deferred <- Deferred[IO, String]
      effect: IO[Int] = IO.raiseError(TestException("failure"))
      _ = effect.runWith(
            onSuccess = _ => (),
            onError = e => dispatcher.unsafeRunAndForget(deferred.complete(e.getMessage))
          )
      result <- deferred.get.timeout(1.second)
    yield assertEquals(result, "failure")

  // ====================
  // runWith() (fire-and-forget) tests
  // ====================

  dispatcherFixture.test("runWith() should complete successfully for successful effects"): dispatcher =>
    given Dispatcher[IO] = dispatcher
    for
      ref <- Ref[IO].of(0)
      effect = ref.set(42)
      _ = effect.runWith()
      _ <- IO.sleep(50.millis) // Allow async execution to complete
      result <- ref.get
    yield assertEquals(result, 42)

  dispatcherFixture.test("runWith() should not throw on failure (errors silently dropped)"): dispatcher =>
    given Dispatcher[IO] = dispatcher
    for
      ref <- Ref[IO].of(false)
      effect: IO[Unit] = IO.raiseError[Unit](TestException("ignored")).guarantee(ref.set(true))
      _ = effect.runWith()
      _ <- IO.sleep(50.millis) // Allow async execution to complete
      // Effect should have run (and failed), triggering guarantee
      executed <- ref.get
    yield assert(executed)

  // ====================
  // Edge cases
  // ====================

  dispatcherFixture.test("runWith should handle effects that return Unit"): dispatcher =>
    given Dispatcher[IO] = dispatcher
    for
      deferred <- Deferred[IO, Either[Throwable, Unit]]
      effect = IO.unit
      _ = effect.runWith(result => dispatcher.unsafeRunAndForget(deferred.complete(result)))
      result <- deferred.get.timeout(1.second)
    yield assertEquals(result, Right(()))

  dispatcherFixture.test("runWith should handle effects that return complex types"): dispatcher =>
    given Dispatcher[IO] = dispatcher
    for
      deferred <- Deferred[IO, Either[Throwable, List[String]]]
      effect = IO.pure(List("a", "b", "c"))
      _ = effect.runWith(result => dispatcher.unsafeRunAndForget(deferred.complete(result)))
      result <- deferred.get.timeout(1.second)
    yield assertEquals(result, Right(List("a", "b", "c")))

end EffectExecutionSpec

/** Test exception type for effect execution tests. */
final case class TestException(message: String) extends Exception(message)

object TestException:
  given CanEqual[TestException, TestException] = CanEqual.derived
