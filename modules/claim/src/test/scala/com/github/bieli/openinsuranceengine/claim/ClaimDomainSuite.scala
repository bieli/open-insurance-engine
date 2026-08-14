package com.github.bieli.openinsuranceengine.claim

import com.github.bieli.openinsuranceengine.core.id.{ClaimTag, CoverageTag, EntityId, PartyTag, PolicyTag}
import com.github.bieli.openinsuranceengine.core.money.{CurrencyCode, Money}
import com.github.bieli.openinsuranceengine.core.time.EffectiveInstant
import munit.FunSuite

import java.time.LocalDate

/** Claim domain enums and money aggregation edge cases. */
class ClaimDomainSuite extends FunSuite:
  private val pln = CurrencyCode.PLN
  private val eur = CurrencyCode.EUR

  private def baseClaim(
      reserves: List[Reserve] = Nil,
      payments: List[ClaimPayment] = Nil
  ): Claim =
    Claim(
      id = EntityId.random[ClaimTag](),
      claimNumber = Some("CLM-1"),
      policyId = EntityId.random[PolicyTag](),
      status = ClaimStatus.Open,
      loss = LossDetails(LocalDate.of(2026, 5, 1), LossType.Collision, "Hit and run"),
      claimantId = EntityId.random[PartyTag](),
      reserves = reserves,
      payments = payments,
      tier = ClaimTier.Low,
      createdAt = EffectiveInstant.now(),
      defaultCurrency = pln
    )

  test("LossType Other preserves carrier-specific code"):
    assertEquals(LossType.Other("HAIL"), LossType.Other("HAIL"))
    assert(LossType.Other("HAIL") != LossType.Glass)

  test("ClaimStatus Reopened is distinct from Open"):
    assert(ClaimStatus.Reopened != ClaimStatus.Open)
    assert(ClaimStatus.Denied != ClaimStatus.Closed)

  test("totalPaid sums multi-payee settlements"):
    val c = baseClaim(
      payments = List(
        ClaimPayment(Money(100_00L, pln), EntityId.random[PartyTag](), EffectiveInstant.now(), Some("SHOP")),
        ClaimPayment(Money(25_50L, pln), EntityId.random[PartyTag](), EffectiveInstant.now(), Some("RENTAL"))
      )
    )
    assertEquals(c.totalPaid, Right(Money(125_50L, pln)))

  test("totalReserves fails on mixed currency exposures"):
    val cov = EntityId.random[CoverageTag]()
    val c = baseClaim(
      reserves = List(
        Reserve(cov, "bi", Money(1000_00L, pln)),
        Reserve(cov, "pd", Money(500_00L, eur))
      )
    )
    assert(c.totalReserves.isLeft)

  test("totalPaid fails on mixed currency payments"):
    val c = baseClaim(
      payments = List(
        ClaimPayment(Money(100_00L, pln), EntityId.random[PartyTag](), EffectiveInstant.now()),
        ClaimPayment(Money(50_00L, eur), EntityId.random[PartyTag](), EffectiveInstant.now())
      )
    )
    assert(c.totalPaid.isLeft)

  test("sumMoney empty list uses fallback currency"):
    assertEquals(Claim.sumMoney(Nil, eur), Right(Money.zero(eur)))

  test("LossDetails optional police report"):
    val withReport = LossDetails(
      LocalDate.of(2026, 1, 2),
      LossType.Theft,
      "Stolen vehicle",
      location = Some("Gdańsk"),
      policeReportNumber = Some("KP-2026/001")
    )
    assertEquals(withReport.policeReportNumber, Some("KP-2026/001"))
    assertEquals(withReport.location, Some("Gdańsk"))
