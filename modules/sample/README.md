# Tausi Sample: Customer Survey Application

A comprehensive sample application demonstrating the Tausi API for building Tauri desktop applications with Scala.js, Laminar, and ZIO.

## Overview

This sample implements a multi-page customer survey application with:

- **Welcome page** - Introduction and survey overview
- **Contact Information** - Collect user details with validation
- **Survey Page 1** - Rating-based questions (1-5 scale)
- **Survey Page 2** - Multi-choice and text response questions
- **Submit** - Review and submit with file persistence

## Architecture

### Package Structure

```
tausi.sample/
├── Main.scala              # Application entry point
├── App.scala               # Root Laminar component
├── commands/
│   └── SurveyCommands.scala  # Tauri command definitions
├── components/
│   ├── Buttons.scala        # Button components
│   ├── FormInputs.scala     # Form input components
│   ├── Layout.scala         # Layout components
│   └── ProgressIndicator.scala
├── config/
│   └── SurveyConfig.scala   # Survey question configuration
├── model/
│   ├── Page.scala           # Navigation state
│   └── SurveyData.scala     # Data models
├── pages/
│   ├── WelcomePage.scala
│   ├── ContactInfoPage.scala
│   ├── SurveyPageOne.scala
│   ├── SurveyPageTwo.scala
│   └── SubmitPage.scala
├── services/
│   ├── SurveyService.scala    # Synchronous service
│   ├── ZioSurveyService.scala # ZIO-based async service
│   └── EventDemos.scala       # Event system demos
├── state/
│   └── AppState.scala       # Reactive state management
└── validation/
    └── Validators.scala     # Form validation logic
```

## Tausi API Usage

### Command Invocation (ZIO)

```scala
import tausi.zio.*
import tausi.sample.commands.survey.{given, *}

// Invoke a command with ZIO
val result: IO[TauriError, Unit] = invoke(SaveSurveyRequest(submission))

// Run with callbacks for Laminar integration
Unsafe.unsafe { implicit unsafe =>
  Runtime.default.unsafe
    .runToFuture(result)
    .future
    .onComplete {
      case Success(_)  => onSuccess()
      case Failure(ex) => onError(TauriError.fromThrowable(ex))
    }(using ExecutionContext.global)
}
```

### Defining Custom Commands

```scala
import tausi.api.{Command, CommandId}
import tausi.api.codec.*

// Request type with Codec derivation
final case class SaveSurveyRequest(submission: SurveySubmission)

object SaveSurveyRequest:
  given Codec[SaveSurveyRequest] = Codec.derived

// Command definition
given saveSurvey: Command[SaveSurveyRequest, Unit] =
  new Command[SaveSurveyRequest, Unit]:
    val id: CommandId = CommandId.unsafe("save_survey")
    given encoder: Encoder[SaveSurveyRequest] = summon[Codec[SaveSurveyRequest]]
    given decoder: Decoder[Unit] = summon[Codec[Unit]]
```

### Event System (ZIO)

```scala
import tausi.zio.events

// Listen for events
val handle: IO[TauriError, EventHandle] = events.listen[SurveyEvent](
  "survey-event",
  msg => handleEvent(msg.payload)
)

// Listen for single event
val once: IO[TauriError, EventHandle] = events.once[String](
  "backend-ready",
  msg => println(s"Backend ready: ${msg.payload}")
)

// Emit events
val emit: IO[TauriError, Unit] = events.emit("frontend-ready", ())
val emitWithPayload: IO[TauriError, Unit] = events.emit("survey-event", payload)
```

### Codec Derivation

```scala
import tausi.api.codec.Codec

// Automatic derivation for case classes
final case class ContactDetails(
  firstName: String,
  lastName: String,
  phoneNumber: String
)

object ContactDetails:
  given Codec[ContactDetails] = Codec.derived

// Enums also supported
enum QuestionType:
  case Rating, Text, MultiChoice

object QuestionType:
  given Codec[QuestionType] = Codec.derived
```

## Laminar Patterns

### Reactive State with Var/Signal

```scala
final class AppState private (
  val currentPage: Var[Page],
  val contactDetails: Var[ContactDetails],
  val surveyAnswers: Var[Map[String, String]]
):
  def navigateNext(): Unit =
    Page.next(currentPage.now()).foreach(navigateTo)
    
  def setAnswer(questionId: String, answer: String): Unit =
    surveyAnswers.update(_ + (questionId -> answer))
```

### Controlled Inputs

```scala
input(
  controlled(
    value <-- valueSignal,
    onInput.mapToValue --> { v => onValueChange(v) }
  )
)
```

### Dynamic Children

```scala
div(
  child <-- currentPage.signal.map { page =>
    renderPage(page)
  }
)

div(
  children <-- answersSignal.map { answers =>
    answers.map(renderAnswer)
  }
)

div(
  child.maybe <-- errorSignal.map {
    case Some(err) => Some(errorComponent(err))
    case None => None
  }
)
```

## Rust Backend

The Rust backend implements the `save_survey` command:

```rust
#[tauri::command]
fn save_survey(app: AppHandle, request: SaveSurveyRequest) -> Result<(), String> {
    let submission = request.submission;
    let surveys_dir = app.path().app_data_dir()?.join("surveys");
    fs::create_dir_all(&surveys_dir)?;
    
    let filename = format!("survey_{}_{}.txt", 
        submission.contact_details.last_name, 
        chrono::Utc::now().format("%Y%m%d_%H%M%S")
    );
    
    fs::write(surveys_dir.join(&filename), format_survey(&submission))?;
    Ok(())
}
```

### Survey File Location

Submitted surveys are saved to the Tauri app data directory under a `surveys/` subdirectory:

| Platform | Location |
|----------|----------|
| **Linux** | `~/.local/share/tausi.sample/surveys/` |
| **macOS** | `~/Library/Application Support/tausi.sample/surveys/` |
| **Windows** | `C:\Users\<User>\AppData\Roaming\tausi.sample\surveys\` |

Files are named using the pattern: `survey_<lastname>_<timestamp>.txt`

Example: `survey_smith_20251220_143052.txt`

To view saved surveys on Linux:
```bash
ls -la ~/.local/share/tausi.sample/surveys/
cat ~/.local/share/tausi.sample/surveys/survey_*.txt
```

## Running the Sample

```bash
# Development
cd modules/sample
npm install
npm run tauri dev

# Build
npm run tauri build
```
