/*
 * Copyright (c) 2025 Tausi contributors.
 * See LICENSE file for terms.
 */
package tausi.sample.pages

import com.raquo.laminar.api.L.*

import tausi.sample.components.*
import tausi.sample.model.ContactDetails
import tausi.sample.state.AppState

/** Contact information page - collects user details with validation. */
object ContactInfoPage:
  def apply(state: AppState): HtmlElement =
    Layout.card(
      Layout.pageHeader("Contact Information", Some("Please provide your details")),
      div(
        cls := "space-y-6",
        // First Name
        FormField(
          labelText = "First Name",
          required = true,
          valueSignal = state.contactDetails.signal.map(_.firstName),
          onUpdate = v => state.contactDetails.update(_.copy(firstName = v)),
          placeholderText = "Enter your first name"
        ),
        // Last Name
        FormField(
          labelText = "Last Name",
          required = true,
          valueSignal = state.contactDetails.signal.map(_.lastName),
          onUpdate = v => state.contactDetails.update(_.copy(lastName = v)),
          placeholderText = "Enter your last name"
        ),
        // Email
        FormField(
          labelText = "Email Address",
          required = true,
          valueSignal = state.contactDetails.signal.map(_.email),
          onUpdate = v => state.contactDetails.update(_.copy(email = v)),
          placeholderText = "you@company.com",
          inputType = "email"
        ),
        // Company
        FormField(
          labelText = "Company",
          required = false,
          valueSignal = state.contactDetails.signal.map(_.company),
          onUpdate = v => state.contactDetails.update(_.copy(company = v)),
          placeholderText = "Your company name (optional)"
        )
      ),
      // Navigation
      div(
        cls := "flex justify-between mt-8",
        Buttons.secondary("Back", () => state.navigatePrevious()),
        Buttons.primary("Continue", () => state.navigateNext(), state.isContactComplete.map(!_))
      )
    )

  private def FormField(
      labelText: String,
      required: Boolean,
      valueSignal: Signal[String],
      onUpdate: String => Unit,
      placeholderText: String,
      inputType: String = "text"
  ): HtmlElement =
    div(
      label(
        cls := "block text-sm font-medium text-text-primary mb-2",
        labelText,
        if required then span(cls := "text-error ml-1", "*") else emptyNode
      ),
      input(
        cls := "w-full px-4 py-3 bg-surface-700 border border-border rounded-lg text-text-primary placeholder-text-muted focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary transition-colors",
        typ := inputType,
        placeholder := placeholderText,
        controlled(
          value <-- valueSignal,
          onInput.mapToValue --> onUpdate
        )
      )
    )
