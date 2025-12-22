# Tausi

**Type-safe Scala 3 toolkit for building Tauri applications.**

Tausi provides a robust, idiomatic Scala.js interface to the Tauri runtime. Unlike wrappers around the TypeScript API, Tausi interacts directly with `window.__TAURI_INTERNALS__`, offering superior type safety while maintaining mimal runtime overhead.

> **Status**: The Scala.js frontend API is ready for experimental use. The Scala Native backend for writing plugins is currently in development.

## Architecture

Tausi bypasses the `@tauri-apps/api` JavaScript layer entirely, communicating directly with the Tauri Rust core via the underlying IPC protocol.

*   **Minimal-Overhead**: Direct access to global IPC methods; no extra bundle size from JS dependencies.
*   **Type-Safe**: All commands, events, and parameters are validated at compile time.
*   **Effect Agnostic**: Core API uses `Future`; modules provided for Cats Effect and ZIO.
*   **Action-Oriented**: API designed around actions (`SetTitle`) rather than generic request objects.

## Installation

Add the following to your `build.sbt`:

```scala
// Core API (Scala.js)
libraryDependencies += "io.github.arashi01" %%% "tausi-api" % "0.0.1-SNAPSHOT"

// Optional: Cats Effect Integration
libraryDependencies += "io.github.arashi01" %%% "tausi-cats" % "0.0.1-SNAPSHOT"

// Optional: ZIO Integration
libraryDependencies += "io.github.arashi01" %%% "tausi-zio" % "0.0.1-SNAPSHOT"

// Optional: Laminar Integration (stream-to-Laminar bridge)
libraryDependencies += "io.github.arashi01" %%% "tausi-laminar" % "0.0.1-SNAPSHOT"
```

## Usage

### Command Invocation

Tausi uses a typeclass-based command system. Commands are defined as types (e.g., `SetTitle`) rather than string literals.

```scala
import tausi.api.core.invoke
import tausi.api.commands.window.{given, *}
import scala.concurrent.ExecutionContext.Implicits.global

// 1. Zero-argument commands (implicit resolution)
val version: Future[String] = invoke(using app.version)

// 2. Parameterised commands
invoke(SetTitle("main", "My App")).map { _ =>
  println("Title updated")
}

// 3. Named parameters
invoke(SetSize(
  label = "main",
  width = 800,
  height = 600
))
```

### Event Handling

Events use a type-safe `Event[A]` abstraction that couples event identity with payload type at the type level, following the same pattern as Commands.

```scala
import tausi.api.Event
import tausi.api.events
import tausi.api.codec.Codec
import scala.concurrent.ExecutionContext.Implicits.global

// 1. Define event payload types
final case class SurveySubmission(data: String) derives Codec

// 2. Define events as given instances
given surveySubmitted: Event[SurveySubmission] = Event.define("survey-submitted")
given backendReady: Event[Unit] = Event.define0("backend-ready")

// 3. Listen - Event resolved implicitly, handler receives Either for error handling
events.listen[SurveySubmission] {
  case Right(msg) => println(s"Received: ${msg.payload.data}")
  case Left(err)  => println(s"Decode error: ${err.message}")
}

// 4. Emit - payload type verified against Event instance
events.emit(SurveySubmission("test data"))

// 5. Listen once for Unit events
events.once[Unit] {
  case Right(_)  => println("Backend ready!")
  case Left(err) => println(s"Error: ${err.message}")
}
```

### Effect System Integration

#### Cats Effect

```scala
import cats.effect.IO
import cats.effect.std.Dispatcher
import tausi.cats.*
import tausi.api.commands.window.{given, *}

val program: IO[Unit] = for
  _ <- invoke(SetTitle("main", "My App"))
  _ <- invoke(SetSize("main", 1024, 768))
  _ <- IO.println("Window configured")
yield ()
```

#### ZIO

```scala
import zio.*
import tausi.zio.*
import tausi.api.commands.window.{given, *}

val program: ZIO[Any, TausiError, Unit] = for
  _ <- invoke(SetTitle("main", "My App"))
  _ <- invoke(SetSize("main", 1024, 768))
  _ <- ZIO.log("Window configured")
yield ()
```

### UI Integration (Effect Execution Extensions)

Both effect modules provide `runWith` extension methods for executing effects from UI event handlers:

#### ZIO with Laminar

```scala
import zio.*
import tausi.zio.*

given Runtime[Any] = Runtime.default

button(
  onClick --> { _ =>
    myCommand.runWith(
      onSuccess = result => updateUI(result),
      onError = err => showError(err.message)
    )
  }
)

// Or with unified Either callback
myEffect.runWith {
  case Right(result) => handleSuccess(result)
  case Left(error) => handleError(error)
}

// Fire-and-forget (errors silently dropped - use with caution)
loggingEffect.runWithUnsafe()
```

#### Cats Effect with Laminar

```scala
import cats.effect.IO
import cats.effect.std.Dispatcher
import tausi.cats.*

given Dispatcher[IO] = ??? // from IOApp or Resource

button(
  onClick --> { _ =>
    myCommand.runWith(
      onSuccess = result => updateUI(result),
      onError = err => showError(err.getMessage)
    )
  }
)
```

### Laminar Integration (Stream-to-Laminar Bridge)

Tausi provides a type-safe bridge between effect streams (ZIO ZStream, fs2 Stream) and Laminar observables:

```scala
// Add dependency
libraryDependencies += "io.github.arashi01" %%% "tausi-laminar" % "0.0.1-SNAPSHOT"
```

```scala
import tausi.laminar.*
import tausi.zio.ZStreamIO
import tausi.zio.ZStreamIO.given
import zio.stream.ZStream

// Create a ZIO stream
val counter: ZStreamIO[Int] = ZStream.iterate(0)(_ + 1).take(10)

// Tier 1: Unsafe (errors logged, dropped) - for prototyping
val eventStream: EventStream[Int] = counter.toStreamUnsafe

// Tier 2: Either-based (errors as values)
val signal: Signal[Either[TausiError, Int]] =
  counter.toSignal(Left(TausiError.StreamError("Loading...")))

// Tier 3: Full state (recommended for production)
val stateSignal: Signal[StreamState[Int]] = counter.toStateSignal
```

The `StreamState` ADT provides full lifecycle visibility:

```scala
enum StreamState[+A]:
  case Running                         // Stream is loading
  case Value(value: A)                 // Latest value received
  case Failed(error: TausiError)       // Stream failed
  case Completed                       // Stream completed (no final value)
  case CompletedWith(value: A)         // Stream completed with final value
```

Usage in Laminar:

```scala
div(
  child <-- myStream.toStateSignal.map {
    case StreamState.Running           => div(cls := "spinner", "Loading...")
    case StreamState.Value(data)       => renderData(data)
    case StreamState.Failed(err)       => div(cls := "error", err.message)
    case StreamState.Completed         => div("Done")
    case StreamState.CompletedWith(d)  => renderData(d)
  }
)
```

## Defining Custom Commands

For custom Tauri plugins, define commands using the `Command.define` factory:

```scala
import tausi.api.Command
import tausi.api.codec.Codec

// 1. Define Parameter Type with Codec derivation
final case class Greet(name: String) derives Codec

// 2. Define Command using the factory method
given greet: Command[Greet, String] = Command.define[Greet, String]("greet")

// 3. Usage
invoke(Greet("Alice"))
```

### Zero-Argument Commands

For commands that take no parameters:

```scala
// Define a zero-argument command
given getVersion: Command0[String] = Command.define0[String]("get_version")

// Usage - no arguments needed
val version: Future[String] = invoke
```

### Command Transformations

Commands can be transformed to work with different types:

```scala
import tausi.api.Command

// Transform response type
val intVersion: Command0[Int] = getVersion.mapRes(_.toInt)

// Transform request type
val simpleGreet: Command[String, String] = greet.contramapReq(name => Greet(name))

// Change command ID (useful for testing/mocking)
val testGreet: Command[Greet, String] = greet.withCommandId("test_greet")
```

## Defining Custom Events

For custom application events, define events using the `Event.define` factory:

```scala
import tausi.api.Event
import tausi.api.codec.Codec

// 1. Define Payload Type with Codec derivation
final case class SurveyProgress(currentPage: Int, totalPages: Int) derives Codec

// 2. Define Event using the factory method
given surveyProgress: Event[SurveyProgress] = Event.define("survey-progress")

// 3. Usage - listen
events.listen[SurveyProgress] {
  case Right(msg) => println(s"Page ${msg.payload.currentPage} of ${msg.payload.totalPages}")
  case Left(err)  => println(s"Error: ${err.message}")
}

// 4. Usage - emit
events.emit(SurveyProgress(2, 5))
```

### Events Without Payload

For events that carry no data:

```scala
// Define a Unit event
given backendReady: Event[Unit] = Event.define0("backend-ready")

// Listen for it
events.once[Unit] {
  case Right(_)  => println("Backend is ready!")
  case Left(err) => println(s"Error: ${err.message}")
}

// Emit it
events.emit(())
```

### Event Transformations

Events can be transformed to work with different payload types:

```scala
import tausi.api.Event

// Transform payload type (bidirectional)
val stringProgress: Event[String] = 
  Event.mapPayload(surveyProgress)(
    sp => s"${sp.currentPage}/${sp.totalPages}",
    str => {
      val parts = str.split("/")
      SurveyProgress(parts(0).toInt, parts(1).toInt)
    }
  )

// Change event name
val renamedEvent: Event[SurveyProgress] = surveyProgress.withName("progress-update")
```

## Roadmap

*   [x] **Core JS API**: Type-safe commands, events, and plugins.
*   [x] **Effect Integration**: Cats Effect and ZIO support.
*   [ ] **Scala Native Backend**: Write Tauri plugins directly in Scala Native (in progress).
*   [ ] **SBT Plugin**: Automate cargo/tauri build integration.

## License

MIT

