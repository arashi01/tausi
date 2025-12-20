/*
 * Copyright (c) 2025 Tausi contributors.
 * See LICENSE file for terms.
 */
package tausi.sample.components

import com.raquo.laminar.api.L.*
import tausi.sample.model.Page

/** Progress indicator component showing survey progress with dark theme. */
object ProgressIndicator:

  /** Render the progress indicator.
    *
    * @param currentPageSignal Signal containing the current page
    */
  def apply(currentPageSignal: Signal[Page]): HtmlElement =
    div(
      cls := "w-full mb-6 sm:mb-8",
      role := "navigation",
      aria.label := "Survey progress",
      // Step indicators
      div(
        cls := "flex justify-between items-center relative px-2",
        // Connection line (background)
        div(
          cls := "absolute top-4 left-4 right-4 h-0.5 bg-surface-600"
        ),
        // Progress line (foreground)
        div(
          cls := "absolute top-4 left-4 h-0.5 bg-primary transition-all duration-500 ease-out",
          width <-- currentPageSignal.map { page =>
            // Calculate width as percentage of the connection line
            val progress = (page.stepNumber - 1).toDouble / (Page.totalSteps - 1).toDouble * 100
            s"calc(${progress}% - ${if progress == 100 then 0 else 0}px)"
          }
        ),
        // Step circles
        Page.values.toList.map { page =>
          stepCircle(page, currentPageSignal)
        }
      ),
      // Step labels
      div(
        cls := "hidden sm:flex justify-between mt-3 px-2",
        Page.values.toList.map { page =>
          stepLabel(page, currentPageSignal)
        }
      )
    )

  private def stepCircle(page: Page, currentPageSignal: Signal[Page]): HtmlElement =
    div(
      cls <-- currentPageSignal.map { current =>
        val baseClasses = "relative z-10 w-8 h-8 rounded-full flex items-center justify-center text-sm font-semibold transition-all duration-300"
        val stepNum = page.stepNumber
        val currentNum = current.stepNumber

        if stepNum < currentNum then
          // Completed step
          s"$baseClasses bg-primary text-surface-900"
        else if stepNum == currentNum then
          // Current step
          s"$baseClasses bg-primary text-surface-900 ring-4 ring-primary/30"
        else
          // Future step
          s"$baseClasses bg-surface-600 text-text-muted"
      },
      aria.current <-- currentPageSignal.map { current =>
        if page.stepNumber == current.stepNumber then "step" else ""
      },
      child <-- currentPageSignal.map { current =>
        val stepNum = page.stepNumber
        val currentNum = current.stepNumber
        if stepNum < currentNum then checkIcon
        else span(stepNum.toString)
      }
    )

  private def stepLabel(page: Page, currentPageSignal: Signal[Page]): HtmlElement =
    span(
      cls <-- currentPageSignal.map { current =>
        val baseClasses = "text-xs text-center max-w-20 leading-tight transition-colors duration-300"
        if page.stepNumber < current.stepNumber then
          s"$baseClasses text-primary"
        else if page.stepNumber == current.stepNumber then
          s"$baseClasses text-primary font-medium"
        else
          s"$baseClasses text-text-muted"
      },
      page.title
    )

  private def checkIcon: Element =
    svg.svg(
      svg.cls := "w-4 h-4",
      svg.viewBox := "0 0 20 20",
      svg.fill := "currentColor",
      svg.path(
        svg.fillRule := "evenodd",
        svg.d := "M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z",
        svg.clipRule := "evenodd"
      )
    )
end ProgressIndicator
