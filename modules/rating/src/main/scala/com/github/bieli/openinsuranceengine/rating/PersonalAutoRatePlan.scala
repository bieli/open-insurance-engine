package com.github.bieli.openinsuranceengine.rating

import com.github.bieli.openinsuranceengine.core.money.{CurrencyCode, Money}
import com.github.bieli.openinsuranceengine.core.product.CoverageType
import com.github.bieli.openinsuranceengine.core.result.DomainResult
import com.github.bieli.openinsuranceengine.core.risk.VehicleRisk

/**
 * Sample Personal Auto rate book - age, experience, claims, credit, region,
 * vehicle age and mileage are weighted and turned into a premium.
 */
object PersonalAutoRatePlan:

  val ageTable: RateTable = RateTable.numeric(
    id = "age",
    name = "Driver age",
    (None, Some(21), "Young (<21)", BigDecimal("1.85")),
    (Some(21), Some(25), "Youth (21-24)", BigDecimal("1.45")),
    (Some(25), Some(30), "Young adult (25-29)", BigDecimal("1.15")),
    (Some(30), Some(50), "Standard (30-49)", BigDecimal("1.00")),
    (Some(50), Some(65), "Mature (50-64)", BigDecimal("0.92")),
    (Some(65), None, "Senior (65+)", BigDecimal("1.10"))
  )

  val experienceTable: RateTable = RateTable.numeric(
    id = "experience",
    name = "Years licensed",
    (None, Some(2), "Novice (<2y)", BigDecimal("1.50")),
    (Some(2), Some(5), "Junior (2-4y)", BigDecimal("1.20")),
    (Some(5), Some(10), "Intermediate (5-9y)", BigDecimal("1.05")),
    (Some(10), None, "Experienced (10y+)", BigDecimal("0.95"))
  )

  val claimsTable: RateTable = RateTable.numeric(
    id = "claims",
    name = "Prior claims (3y)",
    (None, Some(1), "Claim-free", BigDecimal("0.90")),
    (Some(1), Some(2), "1 claim", BigDecimal("1.15")),
    (Some(2), Some(3), "2 claims", BigDecimal("1.40")),
    (Some(3), None, "3+ claims", BigDecimal("1.85"))
  )

  val mileageTable: RateTable = RateTable.numeric(
    id = "mileage",
    name = "Annual mileage",
    (None, Some(8000), "Low (<8k)", BigDecimal("0.88")),
    (Some(8000), Some(15000), "Average (8-15k)", BigDecimal("1.00")),
    (Some(15000), Some(25000), "High (15-25k)", BigDecimal("1.18")),
    (Some(25000), None, "Very high (25k+)", BigDecimal("1.35"))
  )

  val vehicleAgeTable: RateTable = RateTable.numeric(
    id = "vehicle-age",
    name = "Vehicle age",
    (None, Some(3), "New (0-2y)", BigDecimal("1.20")),
    (Some(3), Some(8), "Mid (3-7y)", BigDecimal("1.00")),
    (Some(8), Some(15), "Older (8-14y)", BigDecimal("0.90")),
    (Some(15), None, "Vintage (15y+)", BigDecimal("0.85"))
  )

  val creditTable: RateTable = RateTable(
    id = "credit",
    name = "Credit band",
    bands = List(
      RateBand("Excellent", "Excellent", None, None, BigDecimal("0.85")),
      RateBand("Good", "Good", None, None, BigDecimal("0.92")),
      RateBand("Standard", "Standard", None, None, BigDecimal("1.00")),
      RateBand("Fair", "Fair", None, None, BigDecimal("1.15")),
      RateBand("Poor", "Poor", None, None, BigDecimal("1.35"))
    )
  )

  val regionTable: RateTable = RateTable(
    id = "region",
    name = "Region",
    bands = List(
      RateBand("PL-MZ", "Mazowieckie (Warsaw)", None, None, BigDecimal("1.25")),
      RateBand("PL-MA", "Małopolskie", None, None, BigDecimal("1.10")),
      RateBand("PL-DS", "Dolnośląskie", None, None, BigDecimal("1.08")),
      RateBand("PL-OTHER", "Other regions", None, None, BigDecimal("1.00"))
    )
  )

  /** Heavier weight on age & claims - typical underwriting emphasis. */
  val weightedPlan: RatePlan = RatePlan(
    id = "PA-WEIGHTED-PL-2026",
    name = "Personal Auto Weighted (PL 2026)",
    mode = CombinationMode.WeightedAverage,
    factors = List(
      FactorDefinition.fromNumericTable("AGE", "Driver age", BigDecimal("2.5"), ageTable, r => r.profile.age),
      FactorDefinition.fromNumericTable(
        "EXPERIENCE",
        "Years licensed",
        BigDecimal("1.5"),
        experienceTable,
        r => r.profile.yearsLicensed
      ),
      FactorDefinition.fromNumericTable(
        "CLAIMS",
        "Prior claims",
        BigDecimal("2.0"),
        claimsTable,
        r => r.profile.priorClaimsLast3Years
      ),
      FactorDefinition.fromCategoricalTable(
        "CREDIT",
        "Credit band",
        BigDecimal("1.2"),
        creditTable,
        r => r.profile.creditBand.toString
      ),
      FactorDefinition.fromCategoricalTable(
        "REGION",
        "Region",
        BigDecimal("1.0"),
        regionTable,
        r =>
          val code = r.profile.regionCode
          if regionTable.bands.exists(_.code.equalsIgnoreCase(code)) then code else "PL-OTHER"
      ),
      FactorDefinition.fromNumericTable(
        "MILEAGE",
        "Annual mileage",
        BigDecimal("1.0"),
        mileageTable,
        r =>
          // Safe default for demo when mileage missing; engine validates in production plans
          BigDecimal(r.risk match
            case v: VehicleRisk => v.annualMileage.getOrElse(12000)
            case _              => 12000
          )
      ),
      FactorDefinition(
        code = "VEHICLE_AGE",
        name = "Vehicle age",
        weight = BigDecimal("0.8"),
        extract = req =>
          RatingEngine.Vehicle.ageYears(req).flatMap: years =>
            vehicleAgeTable.lookup(years).map(band => (years.toString, band))
      )
    )
  )

  /** Classic multiplicative ISO-style plan (same tables, product of factors). */
  val multiplicativePlan: RatePlan =
    weightedPlan.copy(
      id = "PA-MULT-PL-2026",
      name = "Personal Auto Multiplicative (PL 2026)",
      mode = CombinationMode.Multiplicative
    )

  val defaultBaseRate: Money = Money.fromMajor(BigDecimal(1000), CurrencyCode.PLN)

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
