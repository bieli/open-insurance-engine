package com.github.bieli.openinsuranceengine.validation

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.syntax.all.*
import com.github.bieli.openinsuranceengine.core.result.DomainError

/**
 * Validation algebra - accumulates all field-level errors (ValidatedNel),
 * similar to domain PC/CC field validation before commit.
 */
type ValidationResult[A] = ValidatedNel[DomainError.ValidationFailed, A]

trait Validator[A]:
  def validate(value: A): ValidationResult[A]
