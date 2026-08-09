package com.github.bieli.openinsuranceengine.core.product

import com.github.bieli.openinsuranceengine.core.id.{CoverageTag, EntityId, ProductTag}
import com.github.bieli.openinsuranceengine.core.money.{CurrencyCode, Money}
import munit.FunSuite

import java.time.LocalDate

/**
 * Product / LoB / coverage model - carrier product catalog behaviours.
 */
class ProductSuite extends FunSuite:
  private val pln = CurrencyCode.PLN

  test("LineOfBusiness Specialty is distinguished by code"):
    val cyberNiche = LineOfBusiness.Specialty("CYBER-SME")
    val cyberOther = LineOfBusiness.Specialty("CYBER-LARGE")
    assert(cyberNiche != cyberOther)
    assertEquals(LineOfBusiness.PersonalAuto, LineOfBusiness.PersonalAuto)

  test("CoverageType Custom preserves product-specific codes"):
    val glass = CoverageType.Custom("GLASS-OEM")
    assertEquals(glass, CoverageType.Custom("GLASS-OEM"))
    assert(glass != CoverageType.Comprehensive)

  test("Personal Auto product exposes mandatory BI and optional Collision"):
    val bi = CoverageTemplate(
      code = "BI",
      coverageType = CoverageType.BodilyInjury,
      defaultLimit = Some(Money.fromMajor(BigDecimal(1000000), pln)),
      defaultDeductible = Some(Money.fromMajor(BigDecimal(500), pln)),
      isMandatory = true
    )
    val collision = CoverageTemplate(
      code = "COLL",
      coverageType = CoverageType.Collision,
      defaultLimit = Some(Money.fromMajor(BigDecimal(50000), pln)),
      defaultDeductible = Some(Money.fromMajor(BigDecimal(1000), pln)),
      isMandatory = false
    )
    val product = ProductDefinition(
      id = EntityId.random[ProductTag](),
      code = "PA-PL-2026",
      name = "Personal Auto Poland 2026",
      lineOfBusiness = LineOfBusiness.PersonalAuto,
      availableCoverages = List(bi, collision),
      effectiveFrom = LocalDate.of(2026, 1, 1),
      effectiveTo = Some(LocalDate.of(2026, 12, 31))
    )
    assertEquals(product.lineOfBusiness, LineOfBusiness.PersonalAuto)
    assertEquals(product.availableCoverages.count(_.isMandatory), 1)
    assert(product.availableCoverages.exists(_.code == "COLL"))
    assertEquals(product.effectiveTo, Some(LocalDate.of(2026, 12, 31)))

  test("open-ended product has no effectiveTo (evergreen rate book)"):
    val product = ProductDefinition(
      id = EntityId.random[ProductTag](),
      code = "HO-BASE",
      name = "Homeowners Base",
      lineOfBusiness = LineOfBusiness.Homeowners,
      availableCoverages = List(
        CoverageTemplate("FIRE", CoverageType.Fire, None, None, isMandatory = true)
      ),
      effectiveFrom = LocalDate.of(2020, 1, 1)
    )
    assertEquals(product.effectiveTo, None)

  test("Coverage instance carries limit, deductible and written premium"):
    val coverage = Coverage(
      id = EntityId.random[CoverageTag](),
      code = "BI",
      coverageType = CoverageType.BodilyInjury,
      limit = Money.fromMajor(BigDecimal(5000000), pln),
      deductible = Money.fromMajor(BigDecimal(0), pln),
      premium = Money.fromMajor(BigDecimal(1250.50), pln)
    )
    assertEquals(coverage.coverageType, CoverageType.BodilyInjury)
    assert(coverage.limit.isPositive)
    assert(coverage.deductible.isZero)
    assertEquals(coverage.premium, Money(125050L, pln))

  test("Commercial Property product can mix Fire, Flood and Theft templates"):
    val templates = List(
      CoverageTemplate("FIRE", CoverageType.Fire, Some(Money.fromMajor(BigDecimal(2000000), pln)), None, true),
      CoverageTemplate("FLOOD", CoverageType.Flood, Some(Money.fromMajor(BigDecimal(500000), pln)), Some(Money.fromMajor(BigDecimal(5000), pln)), false),
      CoverageTemplate("THEFT", CoverageType.Theft, Some(Money.fromMajor(BigDecimal(100000), pln)), Some(Money.fromMajor(BigDecimal(1000), pln)), false)
    )
    val product = ProductDefinition(
      id = EntityId.random[ProductTag](),
      code = "CP-WAREHOUSE",
      name = "Commercial Property Warehouse",
      lineOfBusiness = LineOfBusiness.CommercialProperty,
      availableCoverages = templates,
      effectiveFrom = LocalDate.of(2026, 1, 1)
    )
    assertEquals(product.availableCoverages.map(_.coverageType).toSet,
      Set(CoverageType.Fire, CoverageType.Flood, CoverageType.Theft))
    assert(product.availableCoverages.filter(_.isMandatory).map(_.code) == List("FIRE"))

  test("Workers Compensation LoB is distinct from General Liability"):
    assert(LineOfBusiness.WorkersCompensation != LineOfBusiness.GeneralLiability)
    assert(LineOfBusiness.Cyber != LineOfBusiness.InlandMarine)
