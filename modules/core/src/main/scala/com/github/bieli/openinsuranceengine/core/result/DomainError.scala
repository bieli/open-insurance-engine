package com.github.bieli.openinsuranceengine.core.result

import cats.data.NonEmptyList

sealed trait DomainError:
  def code: String
  def message: String

object DomainError:
  final case class ValidationFailed(code: String, message: String, field: Option[String] = None) extends DomainError

  given CanEqual[DomainError, DomainError] = CanEqual.derived
