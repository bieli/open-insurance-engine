package com.github.bieli.openinsuranceengine.claim

import cats.effect.IO
import com.github.bieli.openinsuranceengine.core.algebra.Repository
import com.github.bieli.openinsuranceengine.core.id.{
  ClaimId,
  ClaimTag,
  CoverageTag,
  EntityId,
  PartyTag,
  PolicyTag
}
import com.github.bieli.openinsuranceengine.core.money.{CurrencyCode, Money}
import com.github.bieli.openinsuranceengine.core.time.{DateRange, EffectiveInstant}
import munit.CatsEffectSuite

import java.time.LocalDate

/**
 * ClaimCenter-style handling: FNOL, reserves at limit boundary, catastrophe escalation,
 * indemnity vs deductible, multi-payee settlement.
 */
class ClaimsHandlingSuite extends CatsEffectSuite:
  private val pln = CurrencyCode.PLN
  private val policyTerm = DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))
  private val biLimit = Money.fromMajor(BigDecimal(500000), pln)
  private val deductible = Money.fromMajor(BigDecimal(1000), pln)

  private def svc =
    Repository.inMemory[IO, ClaimId, Claim](_.id).map(ClaimService[IO](_))

  private def claim(
      lossDate: LocalDate = LocalDate.of(2026, 6, 15),
      tier: ClaimTier = ClaimTier.Medium,
      reserves: List[Reserve] = Nil,
      description: String = "Rear-end collision on DK7"
  ): Claim =
    Claim(
      id = EntityId.random[ClaimTag](),
      claimNumber = None,
      policyId = EntityId.random[PolicyTag](),
      status = ClaimStatus.Draft,
      loss = LossDetails(lossDate, LossType.Collision, description, location = Some("Radom, PL")),
      claimantId = EntityId.random[PartyTag](),
      reserves = reserves,
      payments = Nil,
      tier = tier,
      createdAt = EffectiveInstant.now(),
      defaultCurrency = pln
    )

  test("loss date inside policy term is a valid FNOL candidate"):
    assert(policyTerm.contains(LocalDate.of(2026, 6, 15)))
    assert(!policyTerm.contains(LocalDate.of(2025, 12, 31)))
    assert(!policyTerm.contains(LocalDate.of(2027, 1, 1)))

  test("reserve exactly at coverage limit is accepted; one grosz over is rejected"):
    val covId = EntityId.random[CoverageTag]()
    val atLimit = claim(reserves = List(Reserve(covId, "BI", biLimit)))
    val over =
      claim(reserves = List(Reserve(covId, "BI", Money(biLimit.amountMinor + 1, pln))))

    for
      s <- svc
      ok <- s.openFnol(atLimit, policyInForce = true, coverageLimit = Some(biLimit))
      bad <- s.openFnol(over, policyInForce = true, coverageLimit = Some(biLimit))
    yield
      assert(ok.isRight)
      assert(bad.isLeft)

  test("catastrophe FNOL is escalated to UnderInvestigation"):
    for
      s <- svc
      result <- s.openFnol(claim(tier = ClaimTier.Catastrophe), policyInForce = true, None)
    yield assertEquals(result.map(_.status), Right(ClaimStatus.UnderInvestigation))

  test("indemnity after deductible then multi-payment settlement"):
    val covId = EntityId.random[CoverageTag]()
    val lossAmount = Money.fromMajor(BigDecimal(18500), pln)
    val indemnity = (lossAmount - deductible).toOption.get
    val payeeShop = EntityId.random[PartyTag]()
    val payeeInsured = EntityId.random[PartyTag]()

    for
      s <- svc
      opened <- s.openFnol(claim(), policyInForce = true, Some(biLimit))
      reserved <- s.setReserve(opened.toOption.get, Reserve(covId, "vehicle_damage", indemnity))
      approved <- s.approve(reserved.toOption.get)
      // body shop gets most; insured gets residual rental
      shopPay = Money.fromMajor(BigDecimal(15000), pln)
      residual = (indemnity - shopPay).toOption.get
      p1 <- s.pay(approved.toOption.get, ClaimPayment(shopPay, payeeShop, EffectiveInstant.now(), Some("INV-BODY-991")))
      p2 <- s.pay(p1.toOption.get, ClaimPayment(residual, payeeInsured, EffectiveInstant.now(), Some("RENTAL")))
      closed <- s.close(p2.toOption.get)
    yield
      assertEquals(indemnity, Money.fromMajor(BigDecimal(17500), pln))
      assertEquals(closed.toOption.get.totalPaid, Right(indemnity))
      assertEquals(closed.map(_.status), Right(ClaimStatus.Closed))
      assertEquals(closed.toOption.get.payments.size, 2)

  test("coverage denial after FNOL records reason for regulatory audit"):
    for
      s <- svc
      opened <- s.openFnol(claim(description = "Wear and tear / maintenance"), policyInForce = true, None)
      denied <- s.deny(opened.toOption.get, "Exclusion: mechanical breakdown not covered under Collision")
    yield
      assertEquals(denied.map(_.status), Right(ClaimStatus.Denied))
      assert(denied.toOption.get.denialReason.exists(_.contains("Exclusion")))
      assert(denied.toOption.get.closedAt.isDefined)

  test("cancelled policy cannot accept FNOL"):
    for
      s <- svc
      result <- s.openFnol(claim(), policyInForce = false, Some(biLimit))
    yield
      assert(result.isLeft)
      result.left.foreach: errs =>
        assert(errs.exists(_.code.contains("CLM_POLICY_INFORCE") || errs.head.message.contains("not in force")))
