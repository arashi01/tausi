package tausi.sample

import scala.scalajs.js
import scala.scalajs.js.annotation.*

import org.scalajs.dom
import org.scalajs.dom.{console, document}

import scalatags.JsDom.all.*

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import zio.Runtime
import zio.Unsafe

import tausi.api.*
import tausi.api.codec.{Codec, Decoder, Encoder}
import tausi.api.codec.Decoder.given
import tausi.api.codec.Encoder.given
import tausi.cats as CatsAPI
import tausi.zio as ZioAPI

/** Sample application demonstrating Tausi library usage with both cats-effect and ZIO.
  *
  * Shows:
  *   - Command invocation with error handling
  *   - Event listening and emission
  *   - Typed error handling with IO/ZIO
  *   - Resource management patterns
  */
@JSExportTopLevel("TausiSample")
object Main:

  /** Domain model for counter update events.
    *
    * Automatically derives codec instances for type-safe serialization/deserialization.
    */
  final case class CounterUpdate(count: Int, timestamp: Long)

  object CounterUpdate:
    given CanEqual[CounterUpdate, CounterUpdate] = CanEqual.derived
    given Codec[CounterUpdate] = Codec.derived
  @JSExport
  def main(args: Array[String]): Unit =
    console.log("🚀 Tausi Sample Application Starting...")
    
    // Check if running in Tauri
    if core.isTauri then
      console.log("✅ Running in Tauri environment!")
    else
      console.log("⚠️  Not running in Tauri - some features may not be available")
    
    // Render UI
    document.body.appendChild(content.render)
    
    // Initialize examples
    setupCatsEffectExample()
    setupZioExample()
    setupEventListeners()
    
    console.log("✅ Application initialized")

  // ====================
  // Cats-Effect Examples
  // ====================

  // Define manual commands for the sample since we don't have generated ones for "greet" and "start_counter"
  // In a real app, these would be generated or defined in a proper module
  object SampleCommands:
    final case class Greet(name: String)
    object Greet:
      given Codec[Greet] = Codec.derived
      given CanEqual[Greet, Greet] = CanEqual.derived

    given greet: Command[Greet, String] = new Command[Greet, String]:
      val id = CommandId.unsafe("greet")
      given encoder: Encoder[Greet] = summon[Codec[Greet]]
      given decoder: Decoder[String] = summon[Codec[String]]

    final case class StartCounter(max: Int)
    object StartCounter:
      given Codec[StartCounter] = Codec.derived
      given CanEqual[StartCounter, StartCounter] = CanEqual.derived

    given startCounter: Command[StartCounter, String] = new Command[StartCounter, String]:
      val id = CommandId.unsafe("start_counter")
      given encoder: Encoder[StartCounter] = summon[Codec[StartCounter]]
      given decoder: Decoder[String] = summon[Codec[String]]

    final case class EchoPayload(message: String) derives Codec
    final case class Echo(payload: EchoPayload)
    object Echo:
      given Codec[Echo] = Codec.derived
      given CanEqual[Echo, Echo] = CanEqual.derived

    given echo: Command[Echo, String] = new Command[Echo, String]:
      val id = CommandId.unsafe("echo")
      given encoder: Encoder[Echo] = summon[Codec[Echo]]
      given decoder: Decoder[String] = summon[Codec[String]]

  import SampleCommands.{given, *}

  def setupCatsEffectExample(): Unit =
    val button = document.getElementById("cats-greet-btn").asInstanceOf[dom.html.Button]
    val input = document.getElementById("cats-name-input").asInstanceOf[dom.html.Input]
    val output = document.getElementById("cats-output")
    
    if button != null && input != null then
      button.onclick = (_: dom.MouseEvent) =>
        val name = input.value
        if name.nonEmpty then
          val program = for
            _ <- IO(console.log(s"🐱 Cats-Effect: Calling greet command with name: $name"))
            greeting <- CatsAPI.invoke(Greet(name))
            _ <- IO(output.textContent = s"✅ $greeting")
            _ <- IO(console.log(s"🐱 Cats-Effect: Success - $greeting"))
          yield ()
          
          program.handleErrorWith { err =>
            IO {
              output.textContent = s"❌ Error: ${err.getMessage}"
              console.error(s"🐱 Cats-Effect: Error - ${err.getMessage}", err)
            }
          }.unsafeRunAndForget()
        else
          output.textContent = "Please enter a name"
      
      console.log("✅ Cats-Effect example initialized")

  def setupCatsEffectEventExample(): Unit =
    val button = document.getElementById("cats-counter-btn").asInstanceOf[dom.html.Button]
    val output = document.getElementById("cats-event-output")
    
    if button != null then
      button.onclick = (_: dom.MouseEvent) =>
        val program = for
          _ <- IO(console.log("🐱 Cats-Effect: Starting counter with event listeners"))
          _ <- IO(output.textContent = "Counter starting...")
          
          // Listen to counter updates with type-safe decoding
          handle <- CatsAPI.events.listen[CounterUpdate]("counter-update", { event =>
            val update = event.payload
            output.textContent = s"Count: ${update.count} (ts: ${update.timestamp})"
            console.log(s"🐱 Received update: count=${update.count}")
          })
          
          // Listen for completion
          _ <- CatsAPI.events.once[Unit]("counter-finished", { _ =>
            output.textContent = s"${output.textContent}\n✅ Counter finished!"
            console.log("🐱 Counter completed")
          })
          
          // Start the counter
          msg <- CatsAPI.invoke(StartCounter(10))
          _ <- IO(console.log(s"🐱 Counter command: $msg"))
        yield ()
        
        program.handleErrorWith { err =>
          IO {
            output.textContent = s"❌ Error: ${err.getMessage}"
            console.error(s"🐱 Error: ${err.getMessage}", err)
          }
        }.unsafeRunAndForget()
      
      console.log("✅ Cats-Effect event example initialized")

  // ====================
  // ZIO Examples
  // ====================

  def setupZioExample(): Unit =
    val button = document.getElementById("zio-echo-btn").asInstanceOf[dom.html.Button]
    val input = document.getElementById("zio-message-input").asInstanceOf[dom.html.Input]
    val output = document.getElementById("zio-output")
    
    if button != null && input != null then
      button.onclick = (_: dom.MouseEvent) =>
        val message = input.value
        if message.nonEmpty then
          val program = for
            _ <- zio.ZIO.succeed(console.log(s"⚡ ZIO: Calling echo command with: $message"))
            result <- ZioAPI.invoke(Echo(EchoPayload(message)))
            _ <- zio.ZIO.succeed {
              val resultStr = if result == null then "null" else result
              console.log(s"⚡ ZIO: Result: '$resultStr'")
              output.textContent = s"✅ $resultStr"
            }
          yield ()
          
          val runtime = Runtime.default
          Unsafe.unsafe { implicit unsafe =>
            runtime.unsafe.run(program.catchAll { err =>
              zio.ZIO.succeed {
                output.textContent = s"❌ Error: ${err.message}"
                console.error(s"⚡ ZIO: Error - ${err.message}")
              }
            })
          }
        else
          output.textContent = "Please enter a message"
      
      console.log("✅ ZIO example initialized")

  def setupZioEventExample(): Unit =
    val button = document.getElementById("zio-counter-btn").asInstanceOf[dom.html.Button]
    val output = document.getElementById("zio-event-output")
    
    if button != null then
      button.onclick = (_: dom.MouseEvent) =>
        val program = for
          _ <- zio.ZIO.succeed(console.log("⚡ ZIO: Starting counter with event listeners"))
          _ <- zio.ZIO.succeed(output.textContent = "Counter starting...")
          
          // Listen to counter updates with type-safe decoding
          handle <- ZioAPI.events.listen[CounterUpdate]("counter-update", { event =>
            val update = event.payload
            output.textContent = s"Count: ${update.count} (ts: ${update.timestamp})"
            console.log(s"⚡ Received update: count=${update.count}")
          })
          
          // Listen for completion
          _ <- ZioAPI.events.once[Unit]("counter-finished", { _ =>
            output.textContent = s"${output.textContent}\n✅ Counter finished!"
            console.log("⚡ Counter completed")
          })
          
          // Start the counter  
          result <- ZioAPI.invoke(StartCounter(10))
          _ <- zio.ZIO.succeed(console.log(s"⚡ Counter command: $result"))
        yield ()
        
        val runtime = Runtime.default
        Unsafe.unsafe { implicit unsafe =>
          runtime.unsafe.run(program.catchAll { err =>
            zio.ZIO.succeed {
              output.textContent = s"❌ Error: ${err.message}"
              console.error(s"⚡ Error: ${err.message}")
            }
          })
        }
      
      console.log("✅ ZIO event example initialized")

  def setupEventListeners(): Unit =
    // Notify backend that frontend is ready
    CatsAPI.events.emit("frontend-ready")
      .flatMap(_ => IO(console.log("✅ Sent frontend-ready event")))
      .handleErrorWith(err => IO(console.error(s"Failed to emit event: ${err.getMessage}")))
      .unsafeRunAndForget()
    
    // Listen for backend-ready event
    CatsAPI.events.listen[String]("backend-ready", { event =>
      console.log(s"✅ Backend says: ${event.payload}")
    })
      .flatMap(_ => IO(console.log("✅ Listening for backend-ready events")))
      .handleErrorWith(err => IO(console.error(s"Failed to listen: ${err.getMessage}")))
      .unsafeRunAndForget()
    
    // Initialize event examples
    setupCatsEffectEventExample()
    setupZioEventExample()
  
  val content =
    tag("main")(cls := "container",
      h1("Tausi: Scala.js Tauri API"),
      
      div(cls := "row",
        a(href := "https://tauri.app", target := "_blank",
          img(src := "/assets/tauri.svg", cls := "logo tauri", alt := "Tauri logo")),
        a(href := "https://www.scala-js.org", target := "_blank",
          img(src := "/assets/javascript.svg", cls := "logo vanilla", alt := "Scala.js logo"))
      ),

      p("Demonstrating command invocation and event handling with both ", 
        strong("Cats-Effect"), " and ", strong("ZIO")),
      
      hr(),
      
      // Cats-Effect Section
      div(cls := "section",
        h2("🐱 Cats-Effect Examples"),
        
        // Command invocation example
        div(cls := "example",
          h3("Command Invocation"),
          div(cls := "row",
            input(id := "cats-name-input", placeholder := "Enter your name..."),
            button(id := "cats-greet-btn", "Greet (Cats)")
          ),
          p(id := "cats-output", cls := "output", "Result will appear here...")
        ),
        
        // Event handling example
        div(cls := "example",
          h3("Event Handling"),
          button(id := "cats-counter-btn", "Start Counter (Cats)"),
          p(id := "cats-event-output", cls := "output", "Click to start counter with events...")
        )
      ),
      
      hr(),
      
      // ZIO Section
      div(cls := "section",
        h2("⚡ ZIO Examples"),
        
        // Command invocation example
        div(cls := "example",
          h3("Command Invocation"),
          div(cls := "row",
            input(id := "zio-message-input", placeholder := "Enter a message..."),
            button(id := "zio-echo-btn", "Echo (ZIO)")
          ),
          p(id := "zio-output", cls := "output", "Result will appear here...")
        ),
        
        // Event handling example
        div(cls := "example",
          h3("Event Handling"),
          button(id := "zio-counter-btn", "Start Counter (ZIO)"),
          p(id := "zio-event-output", cls := "output", "Click to start counter with events...")
        )
      ),
      
      hr(),
      
      p(em("Check browser console for detailed logs from both effect systems"))
    )
