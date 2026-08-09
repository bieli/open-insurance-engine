package com.github.bieli.openinsuranceengine.core.risk

import com.github.bieli.openinsuranceengine.core.money.{CurrencyCode, Money}
import munit.FunSuite

class RiskSuite extends FunSuite:
  test("VehicleRisk description"):
    val v = VehicleRisk("VIN123", "Toyota", "Corolla", 2019, licensePlate = Some("WA1"))
    assertEquals(v.riskType, "vehicle")
    assertEquals(v.description, "2019 Toyota Corolla (VIN123)")

  test("PropertyRisk description"):
    val p = PropertyRisk("ul. Marszałkowska 1", "Warsaw", "00-001", replacementCost =
      Some(Money.fromMajor(BigDecimal(500000), CurrencyCode.PLN)))
    assertEquals(p.riskType, "property")
    assert(p.description.contains("Warsaw"))

  test("RatingFactors product of empty is 1"):
    assertEquals(RatingFactors(Map.empty).product, BigDecimal(1))

  test("RatingFactors product and combine"):
    val a = RatingFactors(Map("age" -> BigDecimal("1.2"), "claims" -> BigDecimal("1.1")))
    val b = RatingFactors(Map("region" -> BigDecimal("1.05"), "claims" -> BigDecimal("1.5")))
    assertEquals(a.product, BigDecimal("1.32"))
    val combined = a.combine(b)
    assertEquals(combined.get("claims"), Some(BigDecimal("1.5"))) // later wins
    assertEquals(combined.get("age"), Some(BigDecimal("1.2")))
    assertEquals(combined.get("region"), Some(BigDecimal("1.05")))

  test("RatingFactors get missing key"):
    assertEquals(RatingFactors(Map("x" -> BigDecimal(1))).get("y"), None)
