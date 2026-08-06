package com.github.bieli.openinsuranceengine.core.id

import java.util.UUID
import cats.Eq

/** Opaque identifiers used across Policy / Billing / Claim boundaries. */
opaque type EntityId[+A] = UUID

object EntityId:
  def apply[A](uuid: UUID): EntityId[A] = uuid
  def random[A](): EntityId[A] = UUID.randomUUID()
  def fromString[A](s: String): Either[String, EntityId[A]] =
    try Right(UUID.fromString(s))
    catch case _: IllegalArgumentException => Left(s"Invalid EntityId: $s")

  extension [A](id: EntityId[A])
    def value: UUID = id
    def asString: String = id.toString

  given [A]: Eq[EntityId[A]] = Eq.fromUniversalEquals
  given [A]: CanEqual[EntityId[A], EntityId[A]] = CanEqual.derived

sealed trait PolicyTag
sealed trait AccountTag
sealed trait PartyTag
sealed trait ClaimTag
sealed trait InvoiceTag
sealed trait CoverageTag
sealed trait ProductTag
sealed trait QuoteTag
sealed trait PaymentTag
sealed trait WorkflowTag
sealed trait DocumentTag

type PolicyId = EntityId[PolicyTag]
type AccountId = EntityId[AccountTag]
type PartyId = EntityId[PartyTag]
type ClaimId = EntityId[ClaimTag]
type InvoiceId = EntityId[InvoiceTag]
type CoverageId = EntityId[CoverageTag]
type ProductId = EntityId[ProductTag]
type QuoteId = EntityId[QuoteTag]
type PaymentId = EntityId[PaymentTag]
type WorkflowId = EntityId[WorkflowTag]
type DocumentId = EntityId[DocumentTag]
