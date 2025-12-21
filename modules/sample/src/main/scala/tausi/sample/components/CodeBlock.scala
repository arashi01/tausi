/*
 * Copyright (c) 2025 Tausi contributors.
 * See LICENSE file for terms.
 */
package tausi.sample.components

import com.raquo.laminar.api.L.*

/** Code block component for displaying code examples. */
object CodeBlock:
  def apply(title: String, codeText: String): HtmlElement =
    div(
      cls := "mb-6",
      div(
        cls := "flex items-center justify-between px-4 py-2 bg-surface-900 rounded-t-lg border border-border border-b-0",
        span(cls := "text-sm font-medium text-text-secondary", title),
        span(cls := "text-xs text-text-muted", "Scala")
      ),
      pre(
        cls := "p-4 bg-surface-900 rounded-b-lg border border-border overflow-x-auto",
        code(
          cls := "text-sm font-mono text-text-primary leading-relaxed",
          codeText
        )
      )
    )

  def apply(codeText: String): HtmlElement =
    pre(
      cls := "p-4 bg-surface-900 rounded-lg border border-border overflow-x-auto",
      code(
        cls := "text-sm font-mono text-text-primary leading-relaxed",
        codeText
      )
    )
