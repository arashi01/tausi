/*
 * Copyright (c) 2025 Tausi contributors.
 * See LICENSE file for terms.
 */
package tausi.sample.validation

import tausi.sample.model.*

/** Validation logic for form fields and survey data. */
object Validators:

  /** Validate a non-empty string. */
  def nonEmpty(value: String, fieldName: String): ValidationResult =
    if value.trim.nonEmpty then ValidationResult.Valid
    else ValidationResult.Invalid(s"$fieldName is required")

  /** Validate a phone number format. */
  def phoneNumber(value: String): ValidationResult =
    val cleaned = value.replaceAll("[\\s\\-()]", "")
    val phonePattern = "^\\+?[0-9]{10,15}$".r
    if phonePattern.matches(cleaned) then ValidationResult.Valid
    else ValidationResult.Invalid("Please enter a valid phone number")

  /** Validate contact details. */
  def contactDetails(contact: ContactDetails): Map[String, ValidationResult] =
    Map(
      "firstName" -> nonEmpty(contact.firstName, "First name"),
      "lastName" -> nonEmpty(contact.lastName, "Last name"),
      "phoneNumber" -> (
        if contact.phoneNumber.trim.isEmpty then
          ValidationResult.Invalid("Phone number is required")
        else
          phoneNumber(contact.phoneNumber)
      )
    )

  /** Check if contact details are valid. */
  def isContactValid(contact: ContactDetails): Boolean =
    contactDetails(contact).values.forall(_.isValid)

  /** Validate a rating (1-5). */
  def rating(value: Option[String]): ValidationResult =
    value match
      case None => ValidationResult.Invalid("Please select a rating")
      case Some(v) =>
        v.toIntOption match
          case Some(n) if n >= 1 && n <= 5 => ValidationResult.Valid
          case _ => ValidationResult.Invalid("Rating must be between 1 and 5")

  /** Validate a text response. */
  def textResponse(value: Option[String], required: Boolean): ValidationResult =
    if required then
      value match
        case None | Some("") => ValidationResult.Invalid("This field is required")
        case Some(_) => ValidationResult.Valid
    else
      ValidationResult.Valid

  /** Validate a multi-choice selection. */
  def multiChoice(value: Option[String]): ValidationResult =
    value match
      case None | Some("") => ValidationResult.Invalid("Please select an option")
      case Some(_) => ValidationResult.Valid
end Validators
