package com.github.bieli.openinsuranceengine.rating

import cats.effect.Sync
import cats.syntax.all.*
import com.github.bieli.openinsuranceengine.core.result.{DomainError, DomainResult}
import com.github.bieli.openinsuranceengine.core.risk.VehicleRisk
import com.github.bieli.openinsuranceengine.plugins.{BuiltinCapability, Plugin}
import com.github.bieli.openinsuranceengine.policy.PolicyPeriod

/**
 * Applies a RatePlan to each coverage on a PolicyPeriod, using the insured profile
 * and the first vehicle risk. Produces an updated period with recalculated premiums.
 */
final class PolicyRatingPlugin[F[_]: Sync](
    engine: RatingEngine,
    plan: RatePlan,
    profile: InsuredProfile,
    pluginId: String = "personal-auto-rating-v1"
) extends Plugin[F, PolicyPeriod, BuiltinCapability]:

  def id: String = pluginId
  def name: String = plan.name
  def version: String = "1.0.0"
  def capability: BuiltinCapability = BuiltinCapability.Rating

  def execute(ctx: PolicyPeriod): F[DomainResult[PolicyPeriod]] =
    Sync[F].pure(ratePeriod(ctx))

  def ratePeriod(period: PolicyPeriod): DomainResult[PolicyPeriod] =
    period.risks.collectFirst { case v: VehicleRisk => v } match
      case None =>
        DomainResult.raise(
          DomainError.ValidationFailed("RATING_VEHICLE", "Personal auto rating requires a VehicleRisk")
        )
      case Some(vehicle) =>
        period.coverages.traverse: cov =>
          PersonalAutoRatePlan
            .ratePersonalAuto(
              engine = engine,
              profile = profile,
              vehicle = vehicle,
              coverageType = cov.coverageType,
              baseRate = cov.premium, // treat existing premium as coverage base rate
              plan = plan
            )
            .map(result => cov.copy(premium = result.premium) -> result)
        .flatMap: rated =>
          val newCoverages = rated.map(_._1)
          val updated = period.copy(coverages = newCoverages)
          updated.recalculatePremium match
            case Left(err) =>
              DomainResult.raise(DomainError.PluginError("RATING", err, id))
            case Right(p) =>
              DomainResult.pure(p)

object PolicyRatingPlugin:
  /** Rate and also return worksheets for demo / audit logging. */
  def rateWithWorksheets(
      engine: RatingEngine,
      plan: RatePlan,
      profile: InsuredProfile,
      period: PolicyPeriod
  ): DomainResult[(PolicyPeriod, List[RateWorksheet])] =
    period.risks.collectFirst { case v: VehicleRisk => v } match
      case None =>
        DomainResult.raise(
          DomainError.ValidationFailed("RATING_VEHICLE", "Personal auto rating requires a VehicleRisk")
        )
      case Some(vehicle) =>
        period.coverages.traverse: cov =>
          PersonalAutoRatePlan.ratePersonalAuto(
            engine = engine,
            profile = profile,
            vehicle = vehicle,
            coverageType = cov.coverageType,
            baseRate = cov.premium,
            plan = plan
          )
        .flatMap: results =>
          val newCoverages = period.coverages.zip(results).map: (cov, res) =>
            cov.copy(premium = res.premium)
          val worksheets = results.map(_.worksheet)
          val updated = period.copy(coverages = newCoverages)
          updated.recalculatePremium match
            case Left(err) =>
              DomainResult.raise(DomainError.Unexpected("RATING", err))
            case Right(p) =>
              DomainResult.pure((p, worksheets))
