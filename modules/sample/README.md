# Tausi Survey Wizard Sample

A real-world sample application demonstrating the Tausi API for building Tauri desktop applications with Scala.js, Laminar, and ZIO.

## Overview

This sample showcases the core Tausi features through a multi-step survey wizard:

- **Multi-page wizard navigation** with reactive state management
- **Type-safe command invocation** with Rust backend integration
- **Event emission and streaming** with Laminar signal integration
- **Form state management** using Laminar Var/Signal
- **Stream-to-Laminar bridge** demonstrating the `toStateSignal` pattern

## Architecture

### Package Structure

```
tausi.sample/
├── Main.scala                # Application entry point, patterns documented
├── commands/
│   └── SurveyCommands.scala  # Custom Tauri command definitions
├── events/
│   └── SurveyEvents.scala    # Custom event definitions
├── components/
│   ├── Buttons.scala         # Reusable button components
│   ├── CodeBlock.scala       # Code example display
│   ├── EventLog.scala        # Stream-to-Laminar demo (toStateSignal)
│   └── Layout.scala          # Layout components
├── model/
│   ├── DemoModels.scala      # Domain models with Codec derivation
│   └── Page.scala            # Navigation state
├── pages/
│   ├── WelcomePage.scala       # Step 1: Welcome and instructions
│   ├── ContactInfoPage.scala   # Step 2: Contact details form
│   ├── SurveyQuestionsPage.scala # Step 3: Survey questions
│   ├── ReviewPage.scala        # Step 4: Review before submission
│   └── CompletePage.scala      # Step 5: Submit with command invocation
└── state/
    └── AppState.scala        # Reactive state management
```

## Feature Demonstrations

### Command Invocation

Type-safe Tauri command invocation with ZIO integration:

```scala
import tausi.sample.commands.survey.{given, *}
import tausi.sample.model.*
import tausi.zio.*

// 1. Define domain models with Codec derivation
final case class SurveySubmission(
  contactDetails: ContactDetails,
  answers: SurveyAnswers,
  submittedAt: String
) derives Codec

// 2. Define request wrapper (field name must match Rust param)
final case class SaveSurveyRequest(submission: SurveySubmission) derives Codec

// 3. Define command using factory method
given saveSurvey: Command[SaveSurveyRequest, SaveSurveyResponse] =
  Command.define("save_survey")

// 4. Invoke with typed request
invoke(SaveSurveyRequest(submission)).runWith(
  onSuccess = response => showSuccess(s"Saved to: ${response.filePath}"),
  onError = err => showError(err.message)
)
```

### Event Emission

Type-safe event emission for frontend-to-backend communication:

```scala
import tausi.sample.events.SurveyEvents.{given, *}
import tausi.zio.*

// 1. Define event payload with Codec
final case class SurveySubmittedEvent(
  success: Boolean,
  filePath: String,
  error: Option[String]
) derives Codec

// 2. Define event as given instance
given surveySubmitted: Event[SurveySubmittedEvent] =
  Event.define("survey-submitted")

// 3. Emit typed events
events.emit(SurveySubmittedEvent(
  success = true,
  filePath = response.filePath,
  error = None
)).runWith(
  onSuccess = _ => (),
  onError = err => logError(err.message)
)
```

### Stream-to-Laminar Bridge (EventLog Component)

Converting ZIO streams to Laminar signals with full lifecycle visibility:

```scala
import tausi.laminar.*
import tausi.zio.ZStreamIO.given
import com.raquo.laminar.api.L.*

// Create a stream subscription for events
val submissionStream = events.stream[SurveySubmittedEvent]

// Convert to Laminar Signal with full lifecycle state
val stateSignal: Signal[StreamState[EventMessage[SurveySubmittedEvent]]] =
  submissionStream.toStateSignal

// Render based on stream state with exhaustive pattern matching
child <-- stateSignal.map {
  case StreamState.Running =>
    div("Listening for events...")
  case StreamState.Value(msg) =>
    div(s"Event received: ${msg.payload}")
  case StreamState.Failed(err) =>
    div(cls := "text-error", s"Error: ${err.message}")
  case StreamState.Completed =>
    div("Stream completed")
  case StreamState.CompletedWith(last) =>
    div(s"Final event: ${last.payload}")
}
```

#### StreamState ADT

The `StreamState` ADT provides visibility into all lifecycle phases:

```scala
enum StreamState[+A]:
  case Running                         // Stream active, no values yet
  case Value(value: A)                 // Latest value received
  case Failed(error: TauriError)       // Stream terminated with error
  case Completed                       // Stream completed (no final value)
  case CompletedWith(value: A)         // Stream completed with final value
```

## Form Patterns

### Reactive State with Var/Signal

```scala
final case class AppState(
  currentPage: Var[Page],
  contactDetails: Var[ContactDetails],
  surveyAnswers: Var[SurveyAnswers]
)

object AppState:
  def initial: AppState = AppState(
    currentPage = Var(Page.Welcome),
    contactDetails = Var(ContactDetails.empty),
    surveyAnswers = Var(SurveyAnswers.empty)
  )
```

### Controlled Inputs

```scala
input(
  controlled(
    value <-- valueSignal,
    onInput.mapToValue --> valueVar.set
  ),
  onKeyDown --> { e =>
    if e.key == "Enter" then handleSubmit()
  }
)
```

### Dynamic Children

```scala
// Single child based on state
div(
  child <-- currentPage.signal.map(renderPage)
)

// List of children
div(
  children <-- featuresSignal.map { features =>
    features.map(f => renderFeature(f))
  }
)

// Optional child
div(
  child.maybe <-- errorSignal.map(_.map(renderError))
)
```

## Running the Sample

### Prerequisites

- [Rust](https://www.rust-lang.org/tools/install) and Cargo
- [Node.js](https://nodejs.org/) 18+
- [sbt](https://www.scala-sbt.org/) 1.x

### Development

```bash
# From project root
cd modules/sample
npm install
npm run tauri dev
```

The Scala.js code is compiled by Vite via the `scalaJSVite` plugin.

### Production Build

```bash
cd modules/sample
npm run tauri build
```

## Project Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| Laminar | 17.x | Reactive UI framework |
| ZIO | 2.x | Effect system |
| Tauri | 2.x | Desktop framework |
| Vite | 6.x | Build tool |
