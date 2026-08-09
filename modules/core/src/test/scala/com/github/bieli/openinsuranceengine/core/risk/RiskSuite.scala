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
