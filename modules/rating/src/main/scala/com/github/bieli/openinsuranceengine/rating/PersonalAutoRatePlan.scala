package com.github.bieli.openinsuranceengine.rating

import com.github.bieli.openinsuranceengine.core.money.Money
import com.github.bieli.openinsuranceengine.core.product.CoverageType
import com.github.bieli.openinsuranceengine.core.result.DomainResult
import com.github.bieli.openinsuranceengine.core.risk.VehicleRisk

/**
 * Personal Auto rate book compiled from `oie-rules.yaml` (rating section).
 * Change bands, weights or plans in YAML - this object only looks them up.
 */
object PersonalAutoRatePlan:

  def ageTable: RateTable = RateBook.table("age")
  def experienceTable: RateTable = RateBook.table("experience")
  def claimsTable: RateTable = RateBook.table("claims")
  def mileageTable: RateTable = RateBook.table("mileage")
  def vehicleAgeTable: RateTable = RateBook.table("vehicle-age")
  def creditTable: RateTable = RateBook.table("credit")
  def regionTable: RateTable = RateBook.table("region")

  def weightedPlan: RatePlan = RateBook.plan("PA-WEIGHTED-PL-2026")
  def multiplicativePlan: RatePlan = RateBook.plan("PA-MULT-PL-2026")

  def defaultBaseRate: Money = RateBook.defaultBaseRate

  def ratePersonalAuto(
      engine: RatingEngine,
      profile: InsuredProfile,
      vehicle: VehicleRisk,
      coverageType: CoverageType = CoverageType.BodilyInjury,
      baseRate: Money = defaultBaseRate,
      plan: RatePlan = weightedPlan
  ): DomainResult[RatingResult] =
    engine.rate(
      plan,
      RatingRequest(
        profile = profile,
        risk = vehicle,
        coverageType = coverageType,
        baseRate = baseRate
      )
    )
