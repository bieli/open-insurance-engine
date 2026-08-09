package com.github.bieli.openinsuranceengine.policy

import cats.effect.Sync
import cats.syntax.all.*

import com.github.bieli.openinsuranceengine.core.algebra.Repository
import com.github.bieli.openinsuranceengine.core.id.{AccountId, CoverageId, PartyId, PolicyId, ProductId, QuoteId}
import com.github.bieli.openinsuranceengine.core.money.Money
import com.github.bieli.openinsuranceengine.core.product.{Coverage, LineOfBusiness}
import com.github.bieli.openinsuranceengine.core.risk.{RiskUnit, VehicleRisk}
import com.github.bieli.openinsuranceengine.core.time.{DateRange, EffectiveInstant}
import com.github.bieli.openinsuranceengine.rules.{Rule, RuleSet}

/** Policy lifecycle - PolicyCenter Job / PolicyPeriod analogue. */
enum PolicyStatus:
  case Quote, Draft, Quoted, Bound, InForce, Cancelled, Expired, NonRenewed, Reinstated

object PolicyStatus:
  given CanEqual[PolicyStatus, PolicyStatus] = CanEqual.derived

enum JobType:
  case Submission, PolicyChange, Renewal, Cancellation, Reinstatement, Rewrite, Audit

object JobType:
  given CanEqual[JobType, JobType] = CanEqual.derived

enum CancellationReason:
  case InsuredRequest, NonPayment, Underwriting, FlatCancel
  case Other(code: String)

object CancellationReason:
  given CanEqual[CancellationReason, CancellationReason] = CanEqual.derived

final case class PolicyTerm(period: DateRange, termNumber: Int)

final case class PolicyPeriod(
    policyId: PolicyId,
    accountId: AccountId,
    productId: ProductId,
    policyNumber: Option[String],
    status: PolicyStatus,
    jobType: JobType,
    term: PolicyTerm,
    lineOfBusiness: LineOfBusiness,
    primaryInsuredId: PartyId,
    coverages: List[Coverage],
    risks: List[RiskUnit],
    totalPremium: Money,
    createdAt: EffectiveInstant,
    boundAt: Option[EffectiveInstant] = None,
    producerCode: Option[String] = None
):
  def withCoverage(c: Coverage): PolicyPeriod =
    copy(coverages = coverages :+ c)

  def recalculatePremium: Either[String, PolicyPeriod] =
    if coverages.isEmpty then Right(copy(totalPremium = Money.zero(totalPremium.currency)))
    else
      coverages.tail
        .foldLeft[Either[String, Money]](Right(coverages.head.premium)): (acc, c) =>
          acc.flatMap(_ + c.premium)
        .map(sum => copy(totalPremium = sum))

final case class Quote(
    id: QuoteId,
    accountId: AccountId,
    productId: ProductId,
    period: PolicyPeriod,
    expiresAt: EffectiveInstant,
    offeredPremium: Money
)

final case class BindRequest(
    quoteId: QuoteId,
    policyNumber: String,
    effectiveDate: java.time.LocalDate
)

final case class Endorsement(
    policyId: PolicyId,
    effectiveDate: java.time.LocalDate,
    description: String,
    premiumDelta: Money,
    coveragesAdded: List[Coverage] = Nil,
    coveragesRemoved: List[CoverageId] = Nil
)

object PolicyRules:
  final case class UnderwritingContext(period: PolicyPeriod, driverAge: Option[Int])

  val personalAutoRuleSet: RuleSet[UnderwritingContext] = RuleSet(
    id = "personal-auto-uw",
    name = "Personal Auto Underwriting",
    rules = List(
      Rule.referWhen[UnderwritingContext](
        id = "young-driver",
        name = "Young driver referral",
        priority = 10,
        predicate = _.driverAge.exists(_ < 21),
        reason = ctx => s"Driver age ${ctx.driverAge.getOrElse("?")} requires underwriting referral"
      )
    )
  )

final class PolicyService[F[_]: Sync](repo: Repository[F, PolicyId, PolicyPeriod]):
  def createDraft(period: PolicyPeriod): F[Either[String, PolicyPeriod]] =
    Sync[F].defer:
      validateForDraft(period) match
        case Left(err) => Sync[F].pure(Left(err))
        case Right(p) =>
          val draft = p.copy(status = PolicyStatus.Draft)
          repo.save(draft).map(Right(_))

  def quote(period: PolicyPeriod): F[Either[String, PolicyPeriod]] =
    if period.status != PolicyStatus.Draft then
      Sync[F].pure(Left(s"Cannot quote from status ${period.status}"))
    else
      val quoted = period.copy(status = PolicyStatus.Quoted)
      repo.save(quoted).map(Right(_))

  def bind(period: PolicyPeriod, policyNumber: String): F[Either[String, PolicyPeriod]] =
    if period.status != PolicyStatus.Quoted then
      Sync[F].pure(Left(s"Cannot bind from status ${period.status}"))
    else if period.coverages.isEmpty then
      Sync[F].pure(Left("Cannot bind policy without coverages"))
    else
      val bound = period.copy(
        status = PolicyStatus.InForce,
        policyNumber = Some(policyNumber),
        boundAt = Some(EffectiveInstant.now())
      )
      repo.save(bound).map(Right(_))

  def cancel(period: PolicyPeriod, reason: CancellationReason): F[Either[String, PolicyPeriod]] =
    if period.status != PolicyStatus.InForce then
      Sync[F].pure(Left(s"Cannot cancel from status ${period.status}"))
    else
      val cancelled = period.copy(status = PolicyStatus.Cancelled)
      repo.save(cancelled).map(Right(_))

  def get(id: PolicyId): F[Option[PolicyPeriod]] = repo.get(id)

  private def validateForDraft(period: PolicyPeriod): Either[String, PolicyPeriod] =
    if period.coverages.isEmpty then Left("Draft must include at least one coverage")
    else if requiresVehicle(period.lineOfBusiness) && !period.risks.exists(_.isInstanceOf[VehicleRisk]) then
      Left("Personal/commercial auto draft requires a vehicle risk")
    else Right(period)

  private def requiresVehicle(lob: LineOfBusiness): Boolean =
    lob == LineOfBusiness.PersonalAuto || lob == LineOfBusiness.CommercialAuto

object PolicyService:
  def apply[F[_]: Sync](repo: Repository[F, PolicyId, PolicyPeriod]): PolicyService[F] =
    new PolicyService[F](repo)
