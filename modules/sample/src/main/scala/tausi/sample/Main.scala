/*
 * Copyright (c) 2025 Tausi contributors.
 * See LICENSE file for terms.
 */
package tausi.sample

import com.raquo.laminar.api.L.*
import org.scalajs.dom

import tausi.sample.components.*
import tausi.sample.config.SurveyConfig
import tausi.sample.model.Page
import tausi.sample.pages.*
import tausi.sample.services.SurveyService
import tausi.sample.state.AppState

/** Main application entry point. */
object Main:
  def main(args: Array[String]): Unit =
    // Initialize state and services
    val state = AppState.initial
    val surveyService = SurveyService.live // Use live Tauri backend

    // Render the application
    val appContainer = dom.document.getElementById("app")
    render(appContainer, App(state, surveyService))
end Main

/** Main application component. */
object App:
  def apply(state: AppState, surveyService: SurveyService): HtmlElement =
    Layout.container(
      // Header with company branding
      headerTag(
        cls := "text-center mb-6",
        div(
          cls := "inline-flex items-center gap-2 text-primary font-semibold",
          companyLogo,
          span(SurveyConfig.companyName)
        )
      ),
      // Progress indicator (hidden on welcome page)
      child.maybe <-- state.currentPage.signal.map { page =>
        if page == Page.Welcome then None
        else Some(ProgressIndicator(state.currentPage.signal))
      },
      // Main content area
      mainTag(
        cls := "transition-opacity duration-300",
        child <-- state.currentPage.signal.map { page =>
          renderPage(page, state, surveyService)
        }
      ),
      // Footer
      Layout.footer(SurveyConfig.companyName)
    )

  private def renderPage(
      page: Page,
      state: AppState,
      surveyService: SurveyService
  ): HtmlElement =
    page match
      case Page.Welcome       => WelcomePage(state)
      case Page.ContactInfo   => ContactInfoPage(state)
      case Page.SurveyPageOne => SurveyPageOne(state)
      case Page.SurveyPageTwo => SurveyPageTwo(state)
      case Page.Submit        => SubmitPage(state, surveyService)

  private def companyLogo: Element =
    svg.svg(
      svg.cls := "w-8 h-8",
      svg.viewBox := "0 0 24 24",
      svg.fill := "none",
      svg.stroke := "currentColor",
      svg.strokeWidth := "2",
      svg.path(
        svg.d := "M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4",
        svg.strokeLineCap := "round",
        svg.strokeLineJoin := "round"
      )
    )
end App
