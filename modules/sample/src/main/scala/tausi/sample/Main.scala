/*
 * Copyright (c) 2025 Tausi contributors.
 * See LICENSE file for terms.
 */
package tausi.sample

import com.raquo.laminar.api.L.*
import org.scalajs.dom

import zio.Runtime

import tausi.sample.components.*
import tausi.sample.model.Page
import tausi.sample.pages.*
import tausi.sample.state.AppState

/** Application entry point.
  *
  * This sample demonstrates a real-world Tausi application with the following patterns:
  *
  * ==Command Invocation==
  * Type-safe Tauri command invocation with ZIO integration:
  * {{{
  * import tausi.zio.*
  * import tausi.sample.commands.survey.{given, *}
  *
  * // The wrapper type's field name must match the Rust parameter name
  * invoke(SaveSurveyRequest(submission)).runWith(
  *   onSuccess = response => handleSuccess(response),
  *   onError = err => handleError(err.message)
  * )
  * }}}
  *
  * ==Event Emission==
  * Type-safe event emission for frontend-to-backend communication:
  * {{{
  * import tausi.zio.*
  * import tausi.sample.events.SurveyEvents.{given, *}
  *
  * events.emit(SurveySubmittedEvent(success = true, filePath, None))
  * }}}
  *
  * ==Stream-to-Laminar Bridge==
  * Converting ZIO streams to Laminar signals (see [[EventLog]]):
  * {{{
  * import tausi.laminar.*
  * import tausi.zio.ZStreamIO.given
  *
  * val stateSignal = events.stream[T].toStateSignal
  * child <-- stateSignal.map {
  *   case StreamState.Running => spinner()
  *   case StreamState.Value(msg) => render(msg)
  *   case StreamState.Failed(err) => error(err)
  *   case StreamState.Completed => done()
  *   case StreamState.CompletedWith(last) => final(last)
  * }
  * }}}
  *
  * ==Form State Management==
  * Uses Laminar's Var/Signal for reactive form state with controlled inputs.
  *
  * ==Codec Derivation==
  * All data models use `derives Codec` for automatic JSON serialisation.
  */
object Main:
  // ZIO runtime for effect execution
  given Runtime[Any] = Runtime.default

  def main(args: Array[String]): Unit =
    val state = AppState.initial
    val appContainer = dom.document.getElementById("app")
    render(appContainer, App(state))

/** Main application component with wizard layout. */
object App:
  def apply(state: AppState): HtmlElement =
    div(
      cls := "min-h-screen bg-surface-900 flex flex-col",
      // Header
      Header(),
      // Progress indicator
      ProgressIndicator(state.currentPage),
      // Main content
      mainTag(
        cls := "flex-1 container mx-auto px-4 py-6 max-w-2xl",
        child <-- state.currentPage.signal.map(renderPage(_, state))
      ),
      // Footer
      footerTag(
        cls := "text-center text-sm text-text-muted py-4 border-t border-border",
        "Tausi Sample — Customer Survey Application"
      ),
      // Event log panel - demonstrates stream-to-Laminar integration
      // See EventLog.scala for the StreamState pattern documentation
      EventLog()
    )

  private def renderPage(page: Page, state: AppState): HtmlElement =
    page match
      case Page.Welcome         => WelcomePage(state)
      case Page.ContactInfo     => ContactInfoPage(state)
      case Page.SurveyQuestions => SurveyQuestionsPage(state)
      case Page.Review          => ReviewPage(state)
      case Page.Complete        => CompletePage(state)

/** Application header. */
object Header:
  def apply(): HtmlElement =
    headerTag(
      cls := "bg-surface-800 border-b border-border",
      div(
        cls := "container mx-auto px-4 max-w-2xl",
        div(
          cls := "flex items-center justify-center h-16",
          div(
            cls := "flex items-center gap-3 text-primary font-semibold text-xl",
            Logo(),
            span("Customer Survey")
          )
        )
      )
    )

  private def Logo(): Element =
    svg.svg(
      svg.cls := "w-8 h-8",
      svg.viewBox := "0 0 24 24",
      svg.fill := "none",
      svg.stroke := "currentColor",
      svg.strokeWidth := "2",
      svg.path(
        svg.d := "M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z",
        svg.strokeLineCap := "round",
        svg.strokeLineJoin := "round"
      )
    )

/** Progress indicator showing wizard steps. */
object ProgressIndicator:
  def apply(currentPage: Var[Page]): HtmlElement =
    div(
      cls := "bg-surface-800 py-4",
      div(
        cls := "container mx-auto px-4 max-w-2xl",
        div(
          cls := "flex items-center justify-between",
          Page.all.zipWithIndex.flatMap { case (page, index) =>
            val stepElements = List(StepDot(page, currentPage))
            if index < Page.all.length - 1 then
              stepElements :+ StepConnector(page, currentPage)
            else stepElements
          }
        )
      )
    )

  private def StepDot(page: Page, currentPage: Var[Page]): HtmlElement =
    div(
      cls := "flex flex-col items-center",
      div(
        cls <-- currentPage.signal.map { current =>
          val base = "w-8 h-8 rounded-full flex items-center justify-center text-sm font-medium transition-colors"
          if page.stepNumber < current.stepNumber then
            s"$base bg-primary text-surface-900" // Completed
          else if page.stepNumber == current.stepNumber then
            s"$base bg-primary text-surface-900 ring-2 ring-primary ring-offset-2 ring-offset-surface-800" // Current
          else s"$base bg-surface-600 text-text-muted" // Future
        },
        page.stepNumber.toString
      ),
      span(
        cls := "text-xs text-text-muted mt-1 hidden sm:block",
        page.title
      )
    )

  private def StepConnector(page: Page, currentPage: Var[Page]): HtmlElement =
    div(
      cls <-- currentPage.signal.map { current =>
        val base = "flex-1 h-0.5 mx-2"
        if page.stepNumber < current.stepNumber then s"$base bg-primary"
        else s"$base bg-surface-600"
      }
    )

