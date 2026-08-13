package com.github.bieli.openinsuranceengine.rating

import cats.syntax.all.*
import com.github.bieli.openinsuranceengine.core.result.{DomainError, DomainResult}
import com.github.bieli.openinsuranceengine.core.risk.VehicleRisk

/**
 * A factor definition knows how to read the rating request and resolve a RateBand.
 * `weight` controls contribution under WeightedAverage (and is recorded on the worksheet
 * even in Multiplicative mode for auditability).
 */
final case class FactorDefinition(
    code: String,
    name: String,
    weight: BigDecimal,
    extract: RatingRequest => DomainResult[(String, RateBand)]
)

object FactorDefinition:
  def fromNumericTable(
      code: String,
      name: String,
      weight: BigDecimal,
      table: RateTable,
      valueOf: RatingRequest => BigDecimal,
      formatInput: BigDecimal => String = _.bigDecimal.stripTrailingZeros.toPlainString
  ): FactorDefinition =
    FactorDefinition(
      code = code,
      name = name,
      weight = weight,
      extract = req =>
        val raw = valueOf(req)
        table.lookup(raw).map(band => (formatInput(raw), band))
    )

  def fromCategoricalTable(
      code: String,
      name: String,
      weight: BigDecimal,
      table: RateTable,
      keyOf: RatingRequest => String
  ): FactorDefinition =
    FactorDefinition(
      code = code,
      name = name,
      weight = weight,
      extract = req =>
        val key = keyOf(req)
        table.lookupExact(key).map(band => (key, band))
    )

/** Ordered set of factor definitions = a rate plan for a product / LoB. */
final case class RatePlan(
    id: String,
    name: String,
    mode: CombinationMode,
    factors: List[FactorDefinition]
)

trait RatingEngine:
  def rate(plan: RatePlan, request: RatingRequest): DomainResult[RatingResult]

object RatingEngine:
  def apply(): RatingEngine = new RatingEngine:
    def rate(plan: RatePlan, request: RatingRequest): DomainResult[RatingResult] =
      plan.factors.traverse(_.extract(request)).map: extracted =>
        val lines = plan.factors.zip(extracted).map:
          case (defn, (input, band)) =>
            FactorLine(
              code = defn.code,
              name = defn.name,
              inputValue = input,
              band = band.label,
              factor = band.factor,
              weight = defn.weight
            )
        val combined = combine(plan.mode, lines)
        val premium = request.baseRate * combined
        val worksheet = RateWorksheet(
          coverageType = request.coverageType,
          baseRate = request.baseRate,
          mode = plan.mode,
          lines = lines,
          combinedFactor = combined,
          finalPremium = premium
        )
        RatingResult(request, worksheet)

  private def combine(mode: CombinationMode, lines: List[FactorLine]): BigDecimal =
    if lines.isEmpty then BigDecimal(1)
    else
      mode match
        case CombinationMode.Multiplicative =>
          lines.map(_.factor).product
        case CombinationMode.WeightedAverage =>
          val weightSum = lines.map(_.weight).sum
          if weightSum == 0 then BigDecimal(1)
          else lines.map(_.weightedContribution).sum / weightSum

  /** Helpers to pull vehicle fields out of a RatingRequest. */
  object Vehicle:
    def fromRequest(req: RatingRequest): DomainResult[VehicleRisk] =
      req.risk match
        case v: VehicleRisk => DomainResult.pure(v)
        case other =>
          DomainResult.raise(
            DomainError.ValidationFailed("VEHICLE_RISK", s"Expected VehicleRisk, got ${other.riskType}")
          )

    def ageYears(req: RatingRequest): DomainResult[Int] =
      fromRequest(req).map(v => req.asOf.getYear - v.year)

    def annualMileage(req: RatingRequest): DomainResult[Int] =
      fromRequest(req).flatMap: v =>
        v.annualMileage match
          case Some(m) => DomainResult.pure(m)
          case None =>
            DomainResult.raise(DomainError.ValidationFailed("MILEAGE", "Annual mileage is required for rating"))
