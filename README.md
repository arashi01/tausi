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

Events use opaque types for names and targets to prevent string-typing errors.

```scala
import tausi.api.event
import tausi.api.EventName

// Listen to an event
event.listen[String]("backend-message", msg => {
  println(s"Received: ${msg.payload}")
})

// Emit an event
event.emit("frontend-ready", "Hello from Scala.js")
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

val program: ZIO[Any, TauriError, Unit] = for
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

// Fire-and-forget (errors silently dropped)
loggingEffect.runWith()
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

## Roadmap

*   [x] **Core JS API**: Type-safe commands, events, and plugins.
*   [x] **Effect Integration**: Cats Effect and ZIO support.
*   [ ] **Scala Native Backend**: Write Tauri plugins directly in Scala Native (in progress).
*   [ ] **SBT Plugin**: Automate cargo/tauri build integration.

## License

MIT

