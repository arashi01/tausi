/*
 * Copyright (c) 2025 Tausi contributors.
 * See LICENSE file for terms.
 */
package tausi.sample.components

import com.raquo.laminar.api.L.*

/** Reusable button components with dark corporate Tailwind styling. */
object Buttons:

  /** Base button styling with accessible focus states. */
  val buttonBaseClasses: String =
    "px-5 py-2.5 sm:px-6 sm:py-3 rounded-lg font-medium transition-all duration-200 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-offset-surface-800 disabled:opacity-40 disabled:cursor-not-allowed disabled:pointer-events-none"

  /** Primary button (filled) - high contrast for main actions. */
  def primary(
      text: String,
      action: () => Unit,
      disabledSignal: Signal[Boolean]
  ): HtmlElement =
    button(
      cls := s"$buttonBaseClasses bg-primary text-surface-900 font-semibold hover:bg-primary-hover focus:ring-primary shadow-lg shadow-primary/20 hover:shadow-primary/30",
      typ := "button",
      disabled <-- disabledSignal,
      text,
      onClick --> { _ => action() }
    )

  /** Primary button with static disabled state. */
  def primary(text: String, action: () => Unit): HtmlElement =
    primary(text, action, Val(false))

  /** Secondary button (outlined) - for secondary actions. */
  def secondary(
      text: String,
      action: () => Unit,
      disabledSignal: Signal[Boolean]
  ): HtmlElement =
    button(
      cls := s"$buttonBaseClasses border border-border text-text-secondary hover:bg-surface-600 hover:text-text-primary hover:border-text-muted focus:ring-primary/60",
      typ := "button",
      disabled <-- disabledSignal,
      text,
      onClick --> { _ => action() }
    )

  /** Secondary button with static disabled state. */
  def secondary(text: String, action: () => Unit): HtmlElement =
    secondary(text, action, Val(false))

  /** Ghost button (text only) - minimal visual weight. */
  def ghost(text: String, action: () => Unit): HtmlElement =
    button(
      cls := s"$buttonBaseClasses text-text-muted hover:text-primary hover:bg-surface-700 focus:ring-primary/40",
      typ := "button",
      text,
      onClick --> { _ => action() }
    )

  /** Icon button with text. */
  def withIcon(
      text: String,
      iconSvg: HtmlElement,
      action: () => Unit,
      isPrimary: Boolean
  ): HtmlElement =
    val colorClasses =
      if isPrimary then "bg-primary text-surface-900 font-semibold hover:bg-primary-hover focus:ring-primary shadow-lg shadow-primary/20"
      else "border border-border text-text-secondary hover:bg-surface-600 hover:text-text-primary focus:ring-primary/60"
    button(
      cls := s"$buttonBaseClasses $colorClasses flex items-center justify-center gap-2",
      typ := "button",
      iconSvg,
      span(text),
      onClick --> { _ => action() }
    )
end Buttons
