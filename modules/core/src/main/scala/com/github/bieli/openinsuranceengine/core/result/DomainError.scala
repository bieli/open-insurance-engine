package com.github.bieli.openinsuranceengine.core.result

import cats.data.NonEmptyList

sealed trait DomainError:
  def code: String
  def message: String

object DomainError:
  final case class ValidationFailed(code: String, message: String, field: Option[String] = None) extends DomainError

  given CanEqual[DomainError, DomainError] = CanEqual.derived

type DomainErrors = NonEmptyList[DomainError]

type DomainResult[A] = Either[DomainErrors, A]

object DomainResult:
  def pure[A](a: A): DomainResult[A] = Right(a)
  def raise[A](err: DomainError): DomainResult[A] = Left(NonEmptyList.one(err))
  def raiseAll[A](errs: NonEmptyList[DomainError]): DomainResult[A] = Left(errs)
  def fromOption[A](opt: Option[A], err: => DomainError): DomainResult[A] =
    opt.toRight(NonEmptyList.one(err))
