/*
 * Copyright (c) 2025 Tausi contributors.
 * See LICENSE file for terms.
 */
package tausi.sample.pages

import com.raquo.laminar.api.L.*
import tausi.sample.components.*
import tausi.sample.model.ValidationResult
import tausi.sample.state.AppState
import tausi.sample.validation.Validators

/** Contact information page view. */
object ContactInfoPage:

  def apply(state: AppState): HtmlElement =
    val showValidation = Var(false)

    val firstNameValidation: Signal[Option[ValidationResult]] =
      showValidation.signal.combineWith(state.contactDetails.signal).map {
        case (false, _) => None
        case (true, contact) =>
          Some(Validators.nonEmpty(contact.firstName, "First name"))
      }

    val lastNameValidation: Signal[Option[ValidationResult]] =
      showValidation.signal.combineWith(state.contactDetails.signal).map {
        case (false, _) => None
        case (true, contact) =>
          Some(Validators.nonEmpty(contact.lastName, "Last name"))
      }

    val phoneValidation: Signal[Option[ValidationResult]] =
      showValidation.signal.combineWith(state.contactDetails.signal).map {
        case (false, _) => None
        case (true, contact) =>
          if contact.phoneNumber.trim.isEmpty then
            Some(ValidationResult.Invalid("Phone number is required"))
          else
            Some(Validators.phoneNumber(contact.phoneNumber))
      }

    val isValid: Signal[Boolean] =
      state.contactDetails.signal.map(Validators.isContactValid)

    def handleNext(): Unit =
      showValidation.set(true)
      if Validators.isContactValid(state.contactDetails.now()) then state.navigateNext()

    Layout.card(
      Layout.sectionHeader(
        "Contact Information",
        Some("Please provide your contact details so we can follow up if needed.")
      ),
      form(
        onSubmit.preventDefault --> { _ => handleNext() },
        FormInputs.textInput(
          id = "firstName",
          labelText = "First Name",
          placeholderText = "Enter your first name",
          valueSignal = state.contactDetails.signal.map(_.firstName),
          onValueChange = v => state.updateContactDetails(_.copy(firstName = v)),
          validationSignal = firstNameValidation
        ),
        FormInputs.textInput(
          id = "lastName",
          labelText = "Last Name",
          placeholderText = "Enter your last name",
          valueSignal = state.contactDetails.signal.map(_.lastName),
          onValueChange = v => state.updateContactDetails(_.copy(lastName = v)),
          validationSignal = lastNameValidation
        ),
        FormInputs.phoneInput(
          id = "phoneNumber",
          labelText = "Phone Number",
          valueSignal = state.contactDetails.signal.map(_.phoneNumber),
          onValueChange = v => state.updateContactDetails(_.copy(phoneNumber = v)),
          validationSignal = phoneValidation
        ),
        Layout.buttonGroup(
          Buttons.secondary("Back", () => state.navigatePrevious()),
          Buttons.primary("Continue", () => handleNext())
        )
      )
    )
end ContactInfoPage
