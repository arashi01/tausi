/*
 * Copyright (c) 2025 Tausi contributors.
 * See LICENSE file for terms.
 */
package tausi.sample.components

import com.raquo.laminar.api.L.*
import tausi.sample.model.ValidationResult

/** Reusable form input components with dark corporate Tailwind styling. */
object FormInputs:

  /** Common input styling classes - dark theme with proper contrast. */
  val inputBaseClasses: String =
    "w-full px-4 py-3 bg-surface-700 border rounded-lg text-text-primary placeholder-text-muted transition-all duration-200 focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-primary"

  val inputValidClasses: String =
    "border-border hover:border-text-muted"

  val inputInvalidClasses: String =
    "border-error/60 hover:border-error focus:ring-error/40 focus:border-error"

  val labelClasses: String =
    "block text-sm font-medium text-text-primary mb-2"

  val errorClasses: String =
    "text-sm text-error mt-1.5 flex items-center gap-1.5"

  /** Text input component with ARIA support.
    *
    * @param id Input element id
    * @param labelText Label text to display
    * @param placeholderText Placeholder text
    * @param valueSignal Signal containing the current value
    * @param onValueChange Callback when value changes
    * @param validationSignal Signal containing validation result
    */
  def textInput(
      id: String,
      labelText: String,
      placeholderText: String,
      valueSignal: Signal[String],
      onValueChange: String => Unit,
      validationSignal: Signal[Option[ValidationResult]]
  ): HtmlElement =
    val errorId = s"$id-error"
    div(
      cls := "mb-5",
      label(
        cls := labelClasses,
        forId := id,
        labelText
      ),
      input(
        cls <-- validationSignal.map { validation =>
          val validClass = validation match
            case Some(ValidationResult.Invalid(_)) => inputInvalidClasses
            case _ => inputValidClasses
          s"$inputBaseClasses $validClass"
        },
        idAttr := id,
        nameAttr := id,
        typ := "text",
        placeholder := placeholderText,
        aria.describedBy <-- validationSignal.map {
          case Some(ValidationResult.Invalid(_)) => errorId
          case _ => ""
        },
        aria.invalid <-- validationSignal.map {
          case Some(ValidationResult.Invalid(_)) => "true"
          case _ => "false"
        },
        controlled(
          value <-- valueSignal,
          onInput.mapToValue --> { v => onValueChange(v) }
        )
      ),
      child.maybe <-- validationSignal.map {
        case Some(ValidationResult.Invalid(message)) =>
          Some(p(cls := errorClasses, idAttr := errorId, role := "alert", errorIcon, message))
        case _ => None
      }
    )

  /** Phone number input component with ARIA support.
    *
    * @param id Input element id
    * @param labelText Label text to display
    * @param valueSignal Signal containing the current value
    * @param onValueChange Callback when value changes
    * @param validationSignal Signal containing validation result
    */
  def phoneInput(
      id: String,
      labelText: String,
      valueSignal: Signal[String],
      onValueChange: String => Unit,
      validationSignal: Signal[Option[ValidationResult]]
  ): HtmlElement =
    val errorId = s"$id-error"
    div(
      cls := "mb-5",
      label(
        cls := labelClasses,
        forId := id,
        labelText
      ),
      input(
        cls <-- validationSignal.map { validation =>
          val validClass = validation match
            case Some(ValidationResult.Invalid(_)) => inputInvalidClasses
            case _ => inputValidClasses
          s"$inputBaseClasses $validClass"
        },
        idAttr := id,
        nameAttr := id,
        typ := "tel",
        autoComplete := "tel",
        placeholder := "+254 xxx xxxxxx",
        aria.describedBy <-- validationSignal.map {
          case Some(ValidationResult.Invalid(_)) => errorId
          case _ => ""
        },
        aria.invalid <-- validationSignal.map {
          case Some(ValidationResult.Invalid(_)) => "true"
          case _ => "false"
        },
        controlled(
          value <-- valueSignal,
          onInput.mapToValue --> { v => onValueChange(v) }
        )
      ),
      child.maybe <-- validationSignal.map {
        case Some(ValidationResult.Invalid(message)) =>
          Some(p(cls := errorClasses, idAttr := errorId, role := "alert", errorIcon, message))
        case _ => None
      }
    )

  /** Textarea input component.
    *
    * @param id Input element id
    * @param labelText Label text to display
    * @param placeholderText Placeholder text
    * @param valueSignal Signal containing the current value
    * @param onValueChange Callback when value changes
    * @param required Whether the field is required
    */
  def textAreaInput(
      id: String,
      labelText: String,
      placeholderText: String,
      valueSignal: Signal[String],
      onValueChange: String => Unit,
      required: Boolean
  ): HtmlElement =
    div(
      cls := "mb-5",
      label(
        cls := labelClasses,
        forId := id,
        labelText,
        if !required then span(cls := "text-text-muted font-normal ml-1.5", "(Optional)")
        else emptyNode
      ),
      textArea(
        cls := s"$inputBaseClasses $inputValidClasses min-h-24 resize-y",
        idAttr := id,
        nameAttr := id,
        placeholder := placeholderText,
        aria.required := required,
        controlled(
          value <-- valueSignal,
          onInput.mapToValue --> { v => onValueChange(v) }
        )
      )
    )

  /** Rating input component (1-5 scale) with keyboard support.
    *
    * @param id Input element id
    * @param labelText Label text to display
    * @param valueSignal Signal containing the current value (1-5 as string)
    * @param onValueChange Callback when value changes
    */
  def ratingInput(
      id: String,
      labelText: String,
      valueSignal: Signal[Option[String]],
      onValueChange: String => Unit
  ): HtmlElement =
    div(
      cls := "mb-6",
      role := "group",
      aria.label := labelText,
      label(
        cls := labelClasses,
        labelText
      ),
      div(
        cls := "flex gap-2 mt-2",
        (1 to 5).map { rating =>
          button(
            cls <-- valueSignal.map { current =>
              val isSelected = current.flatMap(_.toIntOption).contains(rating)
              val baseClasses =
                "w-11 h-11 sm:w-12 sm:h-12 rounded-lg font-semibold transition-all duration-200 focus:outline-none focus:ring-2 focus:ring-primary/60 focus:ring-offset-2 focus:ring-offset-surface-800"
              if isSelected then s"$baseClasses bg-primary text-surface-900 shadow-lg shadow-primary/30"
              else s"$baseClasses bg-surface-600 text-text-secondary hover:bg-surface-500 hover:text-text-primary"
            },
            typ := "button",
            aria.label := s"Rate $rating out of 5",
            aria.pressed <-- valueSignal.map(_.flatMap(_.toIntOption).contains(rating).toString),
            rating.toString,
            onClick --> { _ => onValueChange(rating.toString) }
          )
        }
      ),
      div(
        cls := "flex justify-between text-xs text-text-muted mt-2 px-0.5",
        span("Poor"),
        span("Excellent")
      )
    )

  /** Multi-choice select component.
    *
    * @param id Input element id
    * @param labelText Label text to display
    * @param options List of options to display
    * @param valueSignal Signal containing the current value
    * @param onValueChange Callback when value changes
    */
  def multiChoiceInput(
      id: String,
      labelText: String,
      options: List[String],
      valueSignal: Signal[Option[String]],
      onValueChange: String => Unit
  ): HtmlElement =
    div(
      cls := "mb-5",
      label(
        cls := labelClasses,
        forId := id,
        labelText
      ),
      select(
        cls := s"$inputBaseClasses $inputValidClasses cursor-pointer appearance-none bg-[url('data:image/svg+xml;charset=UTF-8,%3csvg%20xmlns%3d%22http%3a//www.w3.org/2000/svg%22%20viewBox%3d%220%200%2020%2020%22%20fill%3d%22%2394a3b8%22%3e%3cpath%20fill-rule%3d%22evenodd%22%20d%3d%22M5.23%207.21a.75.75%200%20011.06.02L10%2011.168l3.71-3.938a.75.75%200%20111.08%201.04l-4.25%204.5a.75.75%200%2001-1.08%200l-4.25-4.5a.75.75%200%2001.02-1.06z%22%20clip-rule%3d%22evenodd%22/%3e%3c/svg%3e')] bg-[length:1.25rem] bg-[right_0.75rem_center] bg-no-repeat pr-10",
        idAttr := id,
        nameAttr := id,
        controlled(
          value <-- valueSignal.map(_.getOrElse("")),
          onChange.mapToValue --> { v => onValueChange(v) }
        ),
        option(value := "", disabled := true, selected := true, "Select an option..."),
        options.map { opt =>
          option(value := opt, opt)
        }
      )
    )

  /** Small error icon for validation messages. */
  private def errorIcon: Element =
    svg.svg(
      svg.cls := "w-4 h-4 flex-shrink-0",
      svg.viewBox := "0 0 20 20",
      svg.fill := "currentColor",
      svg.path(
        svg.fillRule := "evenodd",
        svg.d := "M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z",
        svg.clipRule := "evenodd"
      )
    )
end FormInputs
