package com.github.bieli.openinsuranceengine.rating

import com.github.bieli.openinsuranceengine.core.money.{CurrencyCode, Money}
import com.github.bieli.openinsuranceengine.core.product.CoverageType
import com.github.bieli.openinsuranceengine.core.risk.VehicleRisk
import munit.FunSuite

import java.time.LocalDate

/** RateTable / FactorLine / worksheet edge cases for rate-book engineering. */
class RateTableSuite extends FunSuite:
  private val engine = RatingEngine()
  private val pln = CurrencyCode.PLN

  test("numeric band: lower inclusive, upper exclusive"):
    val table = RateTable.numeric(
      "age",
      "Age",
      (Some(18), Some(25), "18-24", BigDecimal("1.2")),
      (Some(25), Some(30), "25-29", BigDecimal("1.0"))
    )
    assertEquals(table.lookup(18).map(_.label), Right("18-24"))
    assertEquals(table.lookup(24.999).map(_.label), Right("18-24"))
    assertEquals(table.lookup(25).map(_.label), Right("25-29"))

  test("lookup fails when value falls in a gap"):
    val table = RateTable.numeric(
      "gap",
      "Gap",
      (None, Some(10), "low", BigDecimal("1.0")),
      (Some(20), None, "high", BigDecimal("2.0"))
    )
    assert(table.lookup(15).isLeft)

  test("empty table lookup always fails"):
    val empty = RateTable("empty", "Empty", Nil)
    assert(empty.lookup(1).isLeft)
    assert(empty.lookupExact("x").isLeft)

  test("FactorLine weightedContribution is factor * weight"):
    val line = FactorLine("AGE", "Age", "35", "Standard", BigDecimal("1.10"), BigDecimal("2.5"))
    assertEquals(line.weightedContribution, BigDecimal("2.75"))

  test("RateWorksheet.summary includes base, factor and lines"):
    val ws = RateWorksheet(
      coverageType = CoverageType.BodilyInjury,
      baseRate = Money.fromMajor(BigDecimal(1000), pln),
      mode = CombinationMode.WeightedAverage,
      lines = List(
        FactorLine("AGE", "Driver age", "40", "Standard", BigDecimal("1.00"), BigDecimal("2.0"))
      ),
      combinedFactor = BigDecimal("1.00"),
      finalPremium = Money.fromMajor(BigDecimal(1000), pln)
    )
    val text = ws.summary
    assert(text.contains("Base rate"))
    assert(text.contains("Combined factor"))
    assert(text.contains("AGE"))
    assert(text.contains("Final premium"))

  test("RatingResult.premium delegates to worksheet"):
    val vehicle = VehicleRisk("VIN12345678901234", "Opel", "Astra", 2017, annualMileage = Some(10000))
    val profile = InsuredProfile(40, 15, 0, "PL-OTHER", CreditBand.Good)
    val result = engine.rate(
      PersonalAutoRatePlan.weightedPlan,
      RatingRequest(
        profile,
        vehicle,
        CoverageType.BodilyInjury,
        Money.fromMajor(BigDecimal(1000), pln),
        LocalDate.of(2026, 8, 5)
      )
    )
    assert(result.isRight)
    assertEquals(result.map(_.premium), result.map(_.worksheet.finalPremium))

  test("Vehicle.annualMileage required when missing"):
    val req = RatingRequest(
      InsuredProfile(30, 5, 0, "PL-OTHER"),
      VehicleRisk("VIN12345678901234", "Skoda", "Octavia", 2019),
      CoverageType.Collision,
      Money.fromMajor(BigDecimal(500), pln)
    )
    assert(RatingEngine.Vehicle.annualMileage(req).isLeft)

  test("Vehicle.fromRequest succeeds for VehicleRisk"):
    val req = RatingRequest(
      InsuredProfile(30, 5, 0, "PL-OTHER"),
      VehicleRisk("VIN12345678901234", "Skoda", "Octavia", 2019, annualMileage = Some(8000)),
      CoverageType.Collision,
      Money.fromMajor(BigDecimal(500), pln)
    )
    assert(RatingEngine.Vehicle.fromRequest(req).isRight)
    assertEquals(RatingEngine.Vehicle.annualMileage(req), Right(8000))

  test("credit band Poor loads above Excellent"):
    val vehicle = VehicleRisk("VIN12345678901234", "Toyota", "Yaris", 2018, annualMileage = Some(10000))
    val base = Money.fromMajor(BigDecimal(1000), pln)
    def rate(credit: CreditBand) =
      PersonalAutoRatePlan.ratePersonalAuto(
        engine,
        InsuredProfile(35, 10, 0, "PL-OTHER", credit),
        vehicle,
        baseRate = base,
        plan = PersonalAutoRatePlan.weightedPlan
      )
    val poor = rate(CreditBand.Poor).toOption.get.premium.amountMinor
    val excellent = rate(CreditBand.Excellent).toOption.get.premium.amountMinor
    assert(poor > excellent)
