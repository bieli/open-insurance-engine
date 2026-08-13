package com.github.bieli.openinsuranceengine.rating

import com.github.bieli.openinsuranceengine.core.money.{CurrencyCode, Money}
import com.github.bieli.openinsuranceengine.core.product.CoverageType
import com.github.bieli.openinsuranceengine.core.risk.VehicleRisk
import munit.FunSuite

class RatingEngineSuite extends FunSuite:
  private val engine = RatingEngine()
  private val vehicle = VehicleRisk(
    vin = "WVWZZZ1JZYW386752",
    make = "Volkswagen",
    model = "Golf",
    year = 2020,
    annualMileage = Some(12000)
  )
  private val base = Money.fromMajor(BigDecimal(1000), CurrencyCode.PLN)

  private def profile(
      age: Int,
      claims: Int = 0,
      yearsLicensed: Int = 10,
      credit: CreditBand = CreditBand.Good,
      region: String = "PL-MZ"
  ): InsuredProfile =
    InsuredProfile(
      age = age,
      yearsLicensed = yearsLicensed,
      priorClaimsLast3Years = claims,
      regionCode = region,
      creditBand = credit
    )

  test("young high-risk driver gets higher weighted premium than mature claim-free"):
    val young = PersonalAutoRatePlan.ratePersonalAuto(
      engine,
      profile(age = 19, claims = 2, yearsLicensed = 1, credit = CreditBand.Fair),
      vehicle,
      baseRate = base,
      plan = PersonalAutoRatePlan.weightedPlan
    )
    val mature = PersonalAutoRatePlan.ratePersonalAuto(
      engine,
      profile(age = 42, claims = 0, yearsLicensed = 20, credit = CreditBand.Excellent, region = "PL-OTHER"),
      vehicle.copy(annualMileage = Some(7000)),
      baseRate = base,
      plan = PersonalAutoRatePlan.weightedPlan
    )
    assert(young.isRight && mature.isRight)
    val yPrem = young.toOption.get.premium.amountMinor
    val mPrem = mature.toOption.get.premium.amountMinor
    assert(yPrem > mPrem, s"expected young ($yPrem) > mature ($mPrem)")

  test("worksheet lists all weighted factors"):
    val result = PersonalAutoRatePlan.ratePersonalAuto(
      engine,
      profile(age = 35),
      vehicle,
      baseRate = base
    )
    assert(result.isRight)
    val ws = result.toOption.get.worksheet
    assertEquals(ws.mode, CombinationMode.WeightedAverage)
    assertEquals(ws.lines.size, PersonalAutoRatePlan.weightedPlan.factors.size)
    assert(ws.lines.exists(_.code == "AGE"))
    assert(ws.lines.exists(_.code == "CLAIMS"))
    assert(ws.combinedFactor > 0)

  test("multiplicative mode multiplies factors"):
    val result = PersonalAutoRatePlan.ratePersonalAuto(
      engine,
      profile(age = 35, claims = 0),
      vehicle,
      baseRate = base,
      plan = PersonalAutoRatePlan.multiplicativePlan
    )
    assert(result.isRight)
    val ws = result.toOption.get.worksheet
    assertEquals(ws.mode, CombinationMode.Multiplicative)
    val expected = ws.lines.map(_.factor).product
    assertEquals(ws.combinedFactor, expected)

  test("age band lookup for senior"):
    val band = PersonalAutoRatePlan.ageTable.lookup(70)
    assertEquals(band.map(_.label), Right("Senior (65+)"))

  test("age table boundary: exactly 21 is Youth not Young"):
    val at21 = PersonalAutoRatePlan.ageTable.lookup(21)
    assertEquals(at21.map(_.label), Right("Youth (21-24)"))
    val at20 = PersonalAutoRatePlan.ageTable.lookup(20)
    assertEquals(at20.map(_.label), Right("Young (<21)"))

  test("unknown region falls back to PL-OTHER"):
    val result = PersonalAutoRatePlan.ratePersonalAuto(
      engine,
      profile(age = 40, region = "PL-ZZ"),
      vehicle,
      baseRate = base
    )
    assert(result.isRight)
    val regionLine = result.toOption.get.worksheet.lines.find(_.code == "REGION").get
    assertEquals(regionLine.band, "Other regions")

  test("empty rate plan yields factor 1"):
    val empty = RatePlan("empty", "Empty", CombinationMode.Multiplicative, Nil)
    val result = engine.rate(
      empty,
      RatingRequest(profile(30), vehicle, CoverageType.BodilyInjury, base)
    )
    assertEquals(result.map(_.worksheet.combinedFactor), Right(BigDecimal(1)))
    assertEquals(result.map(_.premium), Right(base))

  test("weighted average with zero weights yields factor 1"):
    val plan = RatePlan(
      "zw",
      "Zero weights",
      CombinationMode.WeightedAverage,
      List(
        FactorDefinition.fromNumericTable(
          "AGE",
          "Age",
          BigDecimal(0),
          PersonalAutoRatePlan.ageTable,
          r => r.profile.age
        )
      )
    )
    val result = engine.rate(plan, RatingRequest(profile(30), vehicle, CoverageType.Collision, base))
    assertEquals(result.map(_.worksheet.combinedFactor), Right(BigDecimal(1)))

  test("rate table lookupExact is case-insensitive"):
    val band = PersonalAutoRatePlan.creditTable.lookupExact("excellent")
    assertEquals(band.map(_.label), Right("Excellent"))

  test("rate table lookupExact missing key fails"):
    assert(PersonalAutoRatePlan.creditTable.lookupExact("Unknown").isLeft)

  test("non-vehicle risk fails Vehicle.ageYears"):
    val req = RatingRequest(
      profile(30),
      com.github.bieli.openinsuranceengine.core.risk.GenericRisk("other", "x"),
      CoverageType.Fire,
      base
    )
    assert(RatingEngine.Vehicle.ageYears(req).isLeft)

  test("claims band 3+ applies highest factor"):
    val band = PersonalAutoRatePlan.claimsTable.lookup(5)
    assertEquals(band.map(_.factor), Right(BigDecimal("1.85")))
