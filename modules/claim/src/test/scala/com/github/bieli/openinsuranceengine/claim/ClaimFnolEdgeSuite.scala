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
import com.github.bieli.openinsuranceengine.core.time.EffectiveInstant
import munit.CatsEffectSuite

import java.time.LocalDate

/** FNOL validation, rules, and severity-routing edge cases. */
class ClaimFnolEdgeSuite extends CatsEffectSuite:
  private val pln = CurrencyCode.PLN

  private def baseClaim(
      description: String = "Rear-end collision",
      lossDate: LocalDate = LocalDate.now().minusDays(1),
      tier: ClaimTier = ClaimTier.Low,
      reserves: List[Reserve] = Nil
  ): Claim =
    Claim(
      id = EntityId.random[ClaimTag](),
      claimNumber = None,
      policyId = EntityId.random[PolicyTag](),
      status = ClaimStatus.Draft,
      loss = LossDetails(lossDate, LossType.Collision, description),
      claimantId = EntityId.random[PartyTag](),
      reserves = reserves,
      payments = Nil,
      tier = tier,
      createdAt = EffectiveInstant.now()
    )

  private def svc =
    Repository.inMemory[IO, ClaimId, Claim](_.id).map(ClaimService[IO](_))

  test("rejects blank loss description"):
    for
      s <- svc
      result <- s.openFnol(baseClaim(description = "   "), policyInForce = true, None)
    yield assert(result.isLeft)

  test("rejects future loss date"):
    for
      s <- svc
      result <- s.openFnol(baseClaim(lossDate = LocalDate.now().plusDays(1)), policyInForce = true, None)
    yield assert(result.isLeft)

  test("rejects FNOL when policy is not in force"):
    for
      s <- svc
      result <- s.openFnol(baseClaim(), policyInForce = false, None)
    yield assert(result.isLeft)

  test("rejects when total reserves exceed coverage limit"):
    val over =
      baseClaim(reserves =
        List(
          Reserve(
            EntityId.random[CoverageTag](),
            "indemnity",
            Money.fromMajor(BigDecimal(100_000), pln)
          )
        )
      )
    for
      s <- svc
      result <- s.openFnol(over, policyInForce = true, Some(Money.fromMajor(BigDecimal(50_000), pln)))
    yield assert(result.isLeft)

  test("high severity opens as UnderInvestigation (referral)"):
    for
      s <- svc
      result <- s.openFnol(baseClaim(tier = ClaimTier.High), policyInForce = true, None)
    yield assertEquals(result.map(_.status), Right(ClaimStatus.UnderInvestigation))

  test("catastrophe tier also routes to UnderInvestigation"):
    for
      s <- svc
      result <- s.openFnol(baseClaim(tier = ClaimTier.Catastrophe), policyInForce = true, None)
    yield assertEquals(result.map(_.status), Right(ClaimStatus.UnderInvestigation))

  test("cannot pay from Open before approval"):
    for
      s <- svc
      opened <- s.openFnol(baseClaim(), policyInForce = true, None)
      paid <- s.pay(
        opened.toOption.get,
        ClaimPayment(
          Money.fromMajor(BigDecimal(100), pln),
          EntityId.random[PartyTag](),
          EffectiveInstant.now()
        )
      )
    yield assert(paid.isLeft)

  test("deny closes claim with reason"):
    for
      s <- svc
      opened <- s.openFnol(baseClaim(), policyInForce = true, None)
      denied <- s.deny(opened.toOption.get, "Coverage exclusion")
    yield
      assertEquals(denied.map(_.status), Right(ClaimStatus.Denied))
      assertEquals(denied.map(_.denialReason), Right(Some("Coverage exclusion")))
      assert(denied.toOption.get.closedAt.isDefined)
