package com.github.bieli.openinsuranceengine.rating

import com.github.bieli.openinsuranceengine.core.money.{CurrencyCode, Money}
import com.github.bieli.openinsuranceengine.core.product.CoverageType
import com.github.bieli.openinsuranceengine.core.risk.VehicleRisk
import munit.FunSuite

/**
 * Rate-book regression: fixed inputs must produce stable premiums (carrier change-control).
 * Also covers multi-coverage personal auto rating behaviour.
 */
class RateBookRegressionSuite extends FunSuite:
  private val engine = RatingEngine()
  private val pln = CurrencyCode.PLN
  private val asOf = java.time.LocalDate.of(2026, 8, 5)

  private val golf = VehicleRisk(
    vin = "WVWZZZ1JZYW386752",
    make = "Volkswagen",
    model = "Golf",
    year = 2020, // vehicle age = 6y on asOf -> Mid band 1.00
    annualMileage = Some(12000)
  )

  private val preferred = InsuredProfile(
    age = 35,
    yearsLicensed = 10,
    priorClaimsLast3Years = 0,
    regionCode = "PL-OTHER",
    creditBand = CreditBand.Good
  )

  private val nonStandard = InsuredProfile(
    age = 19,
    yearsLicensed = 1,
    priorClaimsLast3Years = 2,
    regionCode = "PL-MZ",
    creditBand = CreditBand.Fair
  )

  test("golden premium: preferred risk weighted plan (regression lock)"):
    /*
     * Factors (weighted):
     * AGE 35->1.00 w2.5 | EXP 10y->0.95 w1.5 | CLAIMS 0->0.90 w2.0
     * CREDIT Good->0.92 w1.2 | REGION OTHER->1.00 w1.0 | MILEAGE 12k->1.00 w1.0
     * VEHICLE_AGE 6y->1.00 w0.8
     * combined = 9.629 / 10.0 = 0.9629
     * 1000.00 PLN * 0.9629 = 962.90 PLN
     */
    val result = engine.rate(
      PersonalAutoRatePlan.weightedPlan,
      RatingRequest(preferred, golf, CoverageType.BodilyInjury, Money.fromMajor(BigDecimal(1000), pln), asOf)
    )
    assertEquals(result.map(_.premium), Right(Money(96290L, pln)))
    assertEquals(result.map(_.worksheet.combinedFactor), Right(BigDecimal("0.9629")))

  test("golden premium: preferred risk multiplicative plan (ISO-style)"):
    // 1.00*0.95*0.90*0.92*1.00*1.00*1.00 = 0.7866 -> 786.60 PLN
    val result = engine.rate(
      PersonalAutoRatePlan.multiplicativePlan,
      RatingRequest(preferred, golf, CoverageType.BodilyInjury, Money.fromMajor(BigDecimal(1000), pln), asOf)
    )
    assertEquals(result.map(_.premium), Right(Money(78660L, pln)))

  test("non-standard risk loads significantly above preferred (adverse selection)"):
    val pref = engine.rate(
      PersonalAutoRatePlan.weightedPlan,
      RatingRequest(preferred, golf, CoverageType.BodilyInjury, Money.fromMajor(BigDecimal(1000), pln), asOf)
    )
    val ns = engine.rate(
      PersonalAutoRatePlan.weightedPlan,
      RatingRequest(nonStandard, golf.copy(annualMileage = Some(30000)), CoverageType.BodilyInjury, Money.fromMajor(BigDecimal(1000), pln), asOf)
    )
    val prefPrem = pref.toOption.get.premium.amountMinor
    val nsPrem = ns.toOption.get.premium.amountMinor
    // Non-standard should be at least 30% more expensive
    assert(nsPrem > prefPrem * 130 / 100, s"ns=$nsPrem pref=$prefPrem")

  test("multi-coverage package: BI + Collision + Comprehensive each rated"):
    val baseRates = List(
      CoverageType.BodilyInjury -> Money.fromMajor(BigDecimal(800), pln),
      CoverageType.Collision -> Money.fromMajor(BigDecimal(600), pln),
      CoverageType.Comprehensive -> Money.fromMajor(BigDecimal(400), pln)
    )
    val premiums = baseRates.map: (cov, base) =>
      engine
        .rate(PersonalAutoRatePlan.weightedPlan, RatingRequest(preferred, golf, cov, base, asOf))
        .map(_.premium.amountMinor)
        .toOption
        .get
    val packagePremium = premiums.sum
    // Each coverage applies same combined factor 0.9629
    assertEquals(premiums(0), 77032L) // 80000 * 0.9629
    assertEquals(premiums(1), 57774L) // 60000 * 0.9629
    assertEquals(premiums(2), 38516L) // 40000 * 0.9629
    assertEquals(packagePremium, 77032L + 57774L + 38516L)

  test("claims surcharge alone increases premium vs claim-free twin"):
    val clean = preferred
    val withClaim = preferred.copy(priorClaimsLast3Years = 1)
    val a = PersonalAutoRatePlan.ratePersonalAuto(engine, clean, golf, baseRate = Money.fromMajor(BigDecimal(1000), pln), plan = PersonalAutoRatePlan.weightedPlan)
    val b = PersonalAutoRatePlan.ratePersonalAuto(engine, withClaim, golf, baseRate = Money.fromMajor(BigDecimal(1000), pln), plan = PersonalAutoRatePlan.weightedPlan)
    assert(b.toOption.get.premium.amountMinor > a.toOption.get.premium.amountMinor)

  test("worksheet is auditable: every factor line has band and weight"):
    val ws = engine
      .rate(
        PersonalAutoRatePlan.weightedPlan,
        RatingRequest(preferred, golf, CoverageType.BodilyInjury, Money.fromMajor(BigDecimal(1000), pln), asOf)
      )
      .toOption
      .get
      .worksheet
    assert(ws.lines.forall(_.weight > 0))
    assert(ws.lines.forall(_.band.nonEmpty))
    assert(ws.summary.contains("Combined factor"))
