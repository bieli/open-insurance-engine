package com.github.bieli.openinsuranceengine.rating

import com.github.bieli.openinsuranceengine.core.money.Money
import com.github.bieli.openinsuranceengine.core.product.CoverageType
import com.github.bieli.openinsuranceengine.core.risk.RiskUnit

/**
 * Snapshot of the insured / driver used by the rating engine.
 * Mirrors typical domain / ISO personal-auto rating inputs.
 */
final case class InsuredProfile(
    age: Int,
    yearsLicensed: Int,
    priorClaimsLast3Years: Int,
    regionCode: String,
    creditBand: CreditBand = CreditBand.Standard,
    gender: Option[String] = None,
    maritalStatus: Option[String] = None,
    attributes: Map[String, String] = Map.empty
)

enum CreditBand:
  case Excellent, Good, Standard, Fair, Poor

object CreditBand:
  given CanEqual[CreditBand, CreditBand] = CanEqual.derived

/** Full rating input: who + what risk + which coverage + base rate. */
final case class RatingRequest(
    profile: InsuredProfile,
    risk: RiskUnit,
    coverageType: CoverageType,
    baseRate: Money,
    asOf: java.time.LocalDate = java.time.LocalDate.now()
)

enum CombinationMode:
  /** Industry-standard auto rating: premium = base x ∏(factor). */
  case Multiplicative
  /** Weighted blend: premium = base x Σ(wᵢ*fᵢ) / Σ(wᵢ). */
  case WeightedAverage

object CombinationMode:
  given CanEqual[CombinationMode, CombinationMode] = CanEqual.derived

/** One applied factor line on the rate worksheet. */
final case class FactorLine(
    code: String,
    name: String,
    inputValue: String,
    band: String,
    factor: BigDecimal,
    weight: BigDecimal
):
  def weightedContribution: BigDecimal = factor * weight

/** Transparent breakdown of how the premium was calculated. */
final case class RateWorksheet(
    coverageType: CoverageType,
    baseRate: Money,
    mode: CombinationMode,
    lines: List[FactorLine],
    combinedFactor: BigDecimal,
    finalPremium: Money
):
  def summary: String =
    val header =
      s"""Rate worksheet (${mode})
         |  Base rate:      $baseRate
         |  Combined factor: $combinedFactor
         |  Final premium:  $finalPremium
         |  Factors:""".stripMargin
    val body = lines
      .map: l =>
        f"    - ${l.code}%-16s ${l.name}%-28s input=${l.inputValue}%-12s band=${l.band}%-16s factor=${l.factor}%5.3f  weight=${l.weight}%5.2f"
      .mkString("\n")
    s"$header\n$body"

final case class RatingResult(
    request: RatingRequest,
    worksheet: RateWorksheet
):
  def premium: Money = worksheet.finalPremium
