package com.github.bieli.openinsuranceengine.claim

import cats.effect.Sync
import cats.syntax.all.*
import com.github.bieli.openinsuranceengine.core.algebra.Repository
import com.github.bieli.openinsuranceengine.core.id.ClaimId
import com.github.bieli.openinsuranceengine.core.money.Money
import com.github.bieli.openinsuranceengine.core.result.{DomainError, DomainResult}
import com.github.bieli.openinsuranceengine.core.time.EffectiveInstant
import com.github.bieli.openinsuranceengine.rules.{Rule, RuleSet}
import com.github.bieli.openinsuranceengine.validation.{Field, Validator}
import com.github.bieli.openinsuranceengine.validation.Validator.toDomainResult

object ClaimValidation:
  val claimValidator: Validator[Claim] = Validator.combine(
    Validator: c =>
      Field.nonBlank("description", c.loss.description).as(c),
    Validator: c =>
      if c.loss.lossDate.isAfter(java.time.LocalDate.now()) then
        com.github.bieli.openinsuranceengine.core.result.DomainError
          .ValidationFailed("LOSS_DATE", "Loss date cannot be in the future", Some("loss.lossDate"))
          .invalidNel
      else c.validNel
  )

object ClaimRules:
  final case class FnolContext(claim: Claim, policyInForce: Boolean, coverageLimit: Option[Money])

  val policyMustBeInForce: Rule[FnolContext] =
    Rule.rejectWhen(
      id = "CLM_POLICY_INFORCE",
      name = "Policy must be in force",
      priority = 1,
      predicate = ctx => !ctx.policyInForce,
      reason = _ => "Cannot open claim against a policy that is not in force"
    )

  val reserveWithinLimit: Rule[FnolContext] =
    Rule.rejectWhen(
      id = "CLM_RESERVE_LIMIT",
      name = "Reserve within coverage limit",
      priority = 10,
      predicate = ctx =>
        (ctx.coverageLimit, ctx.claim.totalReserves) match
          case (Some(limit), Right(res)) => res.amountMinor > limit.amountMinor
          case _                         => false,
      reason = _ => "Total reserves exceed coverage limit"
    )

  val highSeverityReferral: Rule[FnolContext] =
    Rule.referWhen(
      id = "CLM_HIGH_SEVERITY",
      name = "High severity referral",
      priority = 20,
      predicate = ctx => ctx.claim.tier == ClaimTier.High || ctx.claim.tier == ClaimTier.Catastrophe,
      reason = ctx => s"Claim tier ${ctx.claim.tier} requires specialist handling"
    )

  val fnolRuleSet: RuleSet[FnolContext] =
    RuleSet(
      id = "RS_FNOL",
      name = "First Notice of Loss",
      rules = List(policyMustBeInForce, reserveWithinLimit, highSeverityReferral)
    )

trait ClaimService[F[_]]:
  def openFnol(claim: Claim, policyInForce: Boolean, coverageLimit: Option[Money]): F[DomainResult[Claim]]
  def setReserve(claim: Claim, reserve: Reserve): F[DomainResult[Claim]]
  def approve(claim: Claim): F[DomainResult[Claim]]
  def pay(claim: Claim, payment: ClaimPayment): F[DomainResult[Claim]]
  def close(claim: Claim): F[DomainResult[Claim]]
  def deny(claim: Claim, reason: String): F[DomainResult[Claim]]
  def get(id: ClaimId): F[Option[Claim]]

object ClaimService:
  def apply[F[_]: Sync](repo: Repository[F, ClaimId, Claim]): ClaimService[F] =
    new ClaimService[F]:

      def openFnol(
          claim: Claim,
          policyInForce: Boolean,
          coverageLimit: Option[Money]
      ): F[DomainResult[Claim]] =
        ClaimValidation.claimValidator.validate(claim).toDomainResult match
          case Left(errs) => Sync[F].pure(Left(errs))
          case Right(valid) =>
            val ctx = ClaimRules.FnolContext(valid, policyInForce, coverageLimit)
            ClaimRules.fnolRuleSet.evaluate(ctx, stopOnReject = true) match
              case Left(errs) => Sync[F].pure(Left(errs))
              case Right(result) =>
                val opened = valid.copy(
                  status = if result.referrals.nonEmpty then ClaimStatus.UnderInvestigation else ClaimStatus.Open,
                  claimNumber = valid.claimNumber.orElse(Some(s"CLM-${valid.id.asString.take(8).toUpperCase}"))
                )
                repo.save(opened).map(Right(_))

      def setReserve(claim: Claim, reserve: Reserve): F[DomainResult[Claim]] =
        val updated = claim.copy(reserves = claim.reserves :+ reserve, status = ClaimStatus.Reserved)
        repo.save(updated).map(Right(_))

      def approve(claim: Claim): F[DomainResult[Claim]] =
        if claim.status != ClaimStatus.Reserved && claim.status != ClaimStatus.UnderInvestigation && claim.status != ClaimStatus.Open
        then
          Sync[F].pure(
            DomainResult.raise(DomainError.Conflict("INVALID_STATUS", s"Cannot approve from ${claim.status}"))
          )
        else repo.save(claim.copy(status = ClaimStatus.Approved)).map(Right(_))

      def pay(claim: Claim, payment: ClaimPayment): F[DomainResult[Claim]] =
        if claim.status != ClaimStatus.Approved && claim.status != ClaimStatus.Paid then
          Sync[F].pure(
            DomainResult.raise(DomainError.Conflict("INVALID_STATUS", s"Cannot pay from ${claim.status}"))
          )
        else
          repo.save(claim.copy(payments = claim.payments :+ payment, status = ClaimStatus.Paid)).map(Right(_))

      def close(claim: Claim): F[DomainResult[Claim]] =
        Sync[F].realTimeInstant.flatMap: now =>
          repo
            .save(claim.copy(status = ClaimStatus.Closed, closedAt = Some(EffectiveInstant(now))))
            .map(Right(_))

      def deny(claim: Claim, reason: String): F[DomainResult[Claim]] =
        Sync[F].realTimeInstant.flatMap: now =>
          repo
            .save(
              claim.copy(
                status = ClaimStatus.Denied,
                denialReason = Some(reason),
                closedAt = Some(EffectiveInstant(now))
              )
            )
            .map(Right(_))

      def get(id: ClaimId): F[Option[Claim]] = repo.get(id)
