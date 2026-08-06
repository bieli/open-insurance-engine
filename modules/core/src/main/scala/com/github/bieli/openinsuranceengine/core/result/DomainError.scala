package com.github.bieli.openinsuranceengine.core.result

import cats.data.NonEmptyList

/** Structured domain error used across the engine. */
sealed trait DomainError:
  def code: String
  def message: String

object DomainError:
  final case class ValidationFailed(code: String, message: String, field: Option[String] = None) extends DomainError
  final case class RuleViolation(code: String, message: String, ruleId: String) extends DomainError
  final case class NotFound(code: String, message: String) extends DomainError
  final case class Conflict(code: String, message: String) extends DomainError
  final case class WorkflowError(code: String, message: String, step: Option[String] = None) extends DomainError
  final case class PluginError(code: String, message: String, pluginId: String) extends DomainError
  final case class IntegrationError(code: String, message: String, system: String) extends DomainError
  final case class Unexpected(code: String, message: String) extends DomainError

  given CanEqual[DomainError, DomainError] = CanEqual.derived

type DomainErrors = NonEmptyList[DomainError]
type DomainResult[A] = Either[DomainErrors, A]

object DomainResult:
  def pure[A](a: A): DomainResult[A] = Right(a)
  def raise[A](err: DomainError): DomainResult[A] = Left(NonEmptyList.one(err))
  def raiseAll[A](errs: NonEmptyList[DomainError]): DomainResult[A] = Left(errs)

  def fromOption[A](opt: Option[A], err: => DomainError): DomainResult[A] =
    opt.toRight(NonEmptyList.one(err))
