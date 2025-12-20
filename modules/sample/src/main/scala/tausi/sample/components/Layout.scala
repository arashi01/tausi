/*
 * Copyright (c) 2025 Tausi contributors.
 * See LICENSE file for terms.
 */
package tausi.sample.components

import com.raquo.laminar.api.L.*

/** Layout components for consistent page structure with dark corporate theme. */
object Layout:

  /** Main container for the application. */
  def container(children: Modifier[HtmlElement]*): HtmlElement =
    div(
      cls := "min-h-screen bg-surface-900 scrollbar-dark",
      div(
        cls := "container mx-auto px-4 py-6 sm:py-8 max-w-xl",
        children
      )
    )

  /** Card container for content sections. */
  def card(children: Modifier[HtmlElement]*): HtmlElement =
    div(
      cls := "bg-surface-800 rounded-2xl border border-border p-6 sm:p-8 mb-6 shadow-2xl shadow-black/20",
      children
    )

  /** Page header with title and optional subtitle. */
  def pageHeader(title: String, subtitle: Option[String]): HtmlElement =
    div(
      cls := "text-center mb-8",
      h1(
        cls := "text-2xl sm:text-3xl font-bold text-text-primary mb-2",
        title
      ),
      subtitle match
        case Some(sub) =>
          p(
            cls := "text-text-secondary",
            sub
          )
        case None => emptyNode
    )

  /** Section header within a card. */
  def sectionHeader(title: String, description: Option[String]): HtmlElement =
    div(
      cls := "mb-6",
      h2(
        cls := "text-lg sm:text-xl font-semibold text-text-primary mb-1",
        title
      ),
      description match
        case Some(desc) =>
          p(
            cls := "text-text-secondary text-sm",
            desc
          )
        case None => emptyNode
    )

  /** Button group for navigation. */
  def buttonGroup(children: Modifier[HtmlElement]*): HtmlElement =
    div(
      cls := "flex flex-col sm:flex-row justify-between items-stretch sm:items-center gap-3 mt-8 pt-6 border-t border-border",
      children
    )

  /** Footer with company branding. */
  def footer(companyName: String): HtmlElement =
    div(
      cls := "text-center text-sm text-text-muted mt-8",
      p(s"© 2025 $companyName. All rights reserved."),
      p(
        cls := "mt-1",
        "Your feedback is confidential and helps us improve."
      )
    )

  /** Hero section for welcome page. */
  def hero(
      iconContent: Element,
      title: String,
      description: String
  ): HtmlElement =
    div(
      cls := "text-center py-6 sm:py-8",
      div(
        cls := "w-16 h-16 sm:w-20 sm:h-20 mx-auto mb-6 bg-primary/20 rounded-full flex items-center justify-center",
        iconContent
      ),
      h1(
        cls := "text-2xl sm:text-3xl font-bold text-text-primary mb-3",
        title
      ),
      p(
        cls := "text-text-secondary text-base sm:text-lg max-w-md mx-auto leading-relaxed",
        description
      )
    )

  /** Success message container. */
  def successMessage(title: String, message: String): HtmlElement =
    div(
      cls := "text-center py-6 sm:py-8",
      div(
        cls := "w-16 h-16 sm:w-20 sm:h-20 mx-auto mb-6 bg-success/20 rounded-full flex items-center justify-center",
        successIcon
      ),
      h2(
        cls := "text-xl sm:text-2xl font-bold text-text-primary mb-2",
        title
      ),
      p(
        cls := "text-text-secondary",
        message
      )
    )

  private def successIcon: Element =
    svg.svg(
      svg.cls := "w-8 h-8 sm:w-10 sm:h-10 text-success",
      svg.viewBox := "0 0 24 24",
      svg.fill := "none",
      svg.stroke := "currentColor",
      svg.strokeWidth := "2",
      svg.path(
        svg.d := "M5 13l4 4L19 7",
        svg.strokeLineCap := "round",
        svg.strokeLineJoin := "round"
      )
    )
end Layout
