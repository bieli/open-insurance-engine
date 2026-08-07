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

object Validator:
  def apply[A](f: A => ValidationResult[A]): Validator[A] = (value: A) => f(value)

  def pure[A]: Validator[A] = (value: A) => value.validNel

  def combine[A](validators: Validator[A]*): Validator[A] =
    (value: A) =>
      validators.toList
        .traverse_(v => v.validate(value))
        .as(value)

  extension [A](v: ValidationResult[A])
    def toDomainResult: Either[NonEmptyList[DomainError], A] =
      v.toEither.leftMap(_.map(e => e: DomainError))

object Field:
  def required[A](field: String, value: Option[A]): ValidationResult[A] =
    value match
      case Some(a) => a.validNel
      case None =>
        DomainError
          .ValidationFailed("REQUIRED", s"Field '$field' is required", Some(field))
          .invalidNel

  def nonBlank(field: String, value: String): ValidationResult[String] =
    if value.trim.nonEmpty then value.validNel
    else
      DomainError
        .ValidationFailed("NON_BLANK", s"Field '$field' must not be blank", Some(field))
        .invalidNel

  def positive(field: String, value: Long): ValidationResult[Long] =
    if value > 0 then value.validNel
    else
      DomainError
        .ValidationFailed("POSITIVE", s"Field '$field' must be positive", Some(field))
        .invalidNel

  def inRange(field: String, value: Int, min: Int, max: Int): ValidationResult[Int] =
    if value >= min && value <= max then value.validNel
    else
      DomainError
        .ValidationFailed("RANGE", s"Field '$field' must be in [$min, $max]", Some(field))
        .invalidNel

  def matches(field: String, value: String, pattern: String): ValidationResult[String] =
    if value.matches(pattern) then value.validNel
    else
      DomainError
        .ValidationFailed("PATTERN", s"Field '$field' does not match pattern $pattern", Some(field))
        .invalidNel
