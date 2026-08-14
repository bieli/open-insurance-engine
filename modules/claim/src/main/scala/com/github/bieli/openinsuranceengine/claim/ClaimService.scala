package com.github.bieli.openinsuranceengine.claim

import cats.effect.Sync
import cats.syntax.all.*
import com.github.bieli.openinsuranceengine.core.algebra.Repository
import com.github.bieli.openinsuranceengine.core.id.ClaimId
import com.github.bieli.openinsuranceengine.core.money.Money
import com.github.bieli.openinsuranceengine.core.result.{DomainError, DomainResult}
import com.github.bieli.openinsuranceengine.core.time.EffectiveInstant
import com.github.bieli.openinsuranceengine.rules.{DeclaredCheck, Fact, RuleCatalog, RuleSet}
import com.github.bieli.openinsuranceengine.validation.{Field, Validator}
import com.github.bieli.openinsuranceengine.validation.Validator.toDomainResult

object ClaimValidation:
  val claimValidator: Validator[Claim] =
    val checks = RuleCatalog.document.claimValidation.map(toValidator)
    if checks.isEmpty then Validator.pure[Claim]
    else Validator.combine(checks*)

  private def toValidator(check: DeclaredCheck): Validator[Claim] =
    Validator: claim =>
      check.check.trim.toLowerCase match
        case "nonblank" =>
          Field.nonBlank(check.field, stringField(claim, check.field)).as(claim)
        case "notinfuture" =>
          if dateField(claim, check.field).isAfter(java.time.LocalDate.now()) then
            DomainError.ValidationFailed(check.id, check.message, Some(check.field)).invalidNel
          else claim.validNel
        case other =>
          DomainError
            .ValidationFailed("UNKNOWN_CHECK", s"Unknown claim validation '$other'", Some(check.field))
            .invalidNel

  private def stringField(claim: Claim, field: String): String =
    field.trim.toLowerCase match
      case "description" | "loss.description" => claim.loss.description
      case _                                  => ""

  private def dateField(claim: Claim, field: String): java.time.LocalDate =
    field.trim.toLowerCase match
      case "lossdate" | "loss.lossdate" => claim.loss.lossDate
      case _                            => java.time.LocalDate.now()

object ClaimRules:
  final case class FnolContext(claim: Claim, policyInForce: Boolean, coverageLimit: Option[Money])

  def facts(ctx: FnolContext): Map[String, Fact] =
    val reservesMajor = ctx.claim.totalReserves.toOption.map(_.toMajor)
    Map(
      "policyInForce" -> Fact.Bool(ctx.policyInForce),
      "totalReserves" -> Fact.fromOptionBig(reservesMajor),
      "coverageLimit" -> Fact.fromOptionBig(ctx.coverageLimit.map(_.toMajor)),
      "tier" -> Fact.Text(ctx.claim.tier.toString)
    )

  lazy val fnolRuleSet: RuleSet[FnolContext] =
    RuleCatalog.compile(RuleCatalog.document.fnol, facts)

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
