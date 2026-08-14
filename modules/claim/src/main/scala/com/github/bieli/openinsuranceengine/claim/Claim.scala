package com.github.bieli.openinsuranceengine.claim

import com.github.bieli.openinsuranceengine.core.id.{ClaimId, CoverageId, PartyId, PolicyId}
import com.github.bieli.openinsuranceengine.core.money.{CurrencyCode, Money}
import com.github.bieli.openinsuranceengine.core.time.EffectiveInstant

/** ClaimCenter-style claims domain for P&C. */
enum ClaimStatus:
  case Draft, Open, UnderInvestigation, Reserved, Approved, Paid, Closed, Denied, Reopened

object ClaimStatus:
  given CanEqual[ClaimStatus, ClaimStatus] = CanEqual.derived

enum LossType:
  case Collision, Comprehensive, BodilyInjury, PropertyDamage, Theft, Fire, Glass
  case Other(code: String)

object LossType:
  given CanEqual[LossType, LossType] = CanEqual.derived

enum ClaimTier:
  case Low, Medium, High, Catastrophe

object ClaimTier:
  given CanEqual[ClaimTier, ClaimTier] = CanEqual.derived

final case class LossDetails(
    lossDate: java.time.LocalDate,
    lossType: LossType,
    description: String,
    location: Option[String] = None,
    policeReportNumber: Option[String] = None
)

final case class Reserve(
    coverageId: CoverageId,
    exposureType: String,
    amount: Money
)

final case class ClaimPayment(
    amount: Money,
    payeeId: PartyId,
    paidAt: EffectiveInstant,
    reference: Option[String] = None
)

final case class Claim(
    id: ClaimId,
    claimNumber: Option[String],
    policyId: PolicyId,
    status: ClaimStatus,
    loss: LossDetails,
    claimantId: PartyId,
    reserves: List[Reserve],
    payments: List[ClaimPayment],
    tier: ClaimTier,
    createdAt: EffectiveInstant,
    closedAt: Option[EffectiveInstant] = None,
    denialReason: Option[String] = None,
    defaultCurrency: CurrencyCode = CurrencyCode.PLN
):
  def totalReserves: Either[String, Money] =
    Claim.sumMoney(reserves.map(_.amount), defaultCurrency)

  def totalPaid: Either[String, Money] =
    Claim.sumMoney(payments.map(_.amount), defaultCurrency)

object Claim:
  private[claim] def sumMoney(amounts: List[Money], fallback: CurrencyCode): Either[String, Money] =
    amounts match
      case Nil    => Right(Money.zero(fallback))
      case h :: t => t.foldLeft[Either[String, Money]](Right(h))((acc, m) => acc.flatMap(_ + m))
