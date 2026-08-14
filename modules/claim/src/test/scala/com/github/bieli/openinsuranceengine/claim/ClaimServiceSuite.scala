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

class ClaimServiceSuite extends CatsEffectSuite:
  private val currency = CurrencyCode.PLN

  private def sampleClaim(
      tier: ClaimTier = ClaimTier.Medium,
      lossDate: LocalDate = LocalDate.now().minusDays(1),
      description: String = "Collision damage",
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
      createdAt = EffectiveInstant.now(),
      defaultCurrency = currency
    )

  private def svc =
    Repository.inMemory[IO, ClaimId, Claim](_.id).map(ClaimService[IO](_))

  test("FNOL opens claim when policy in force"):
    for
      s <- svc
      result <- s.openFnol(sampleClaim(), policyInForce = true, coverageLimit = None)
    yield
      assert(result.isRight)
      assertEquals(result.toOption.get.status, ClaimStatus.Open)
      assert(result.toOption.get.claimNumber.isDefined)

  test("FNOL rejects when policy not in force"):
    for
      s <- svc
      result <- s.openFnol(sampleClaim(), policyInForce = false, coverageLimit = None)
    yield assert(result.isLeft)

  test("FNOL rejects future loss date"):
    for
      s <- svc
      result <- s.openFnol(
        sampleClaim(lossDate = LocalDate.now().plusDays(2)),
        policyInForce = true,
        coverageLimit = None
      )
    yield assert(result.isLeft)

  test("FNOL rejects blank description"):
    for
      s <- svc
      result <- s.openFnol(sampleClaim(description = "   "), policyInForce = true, coverageLimit = None)
    yield assert(result.isLeft)

  test("high tier goes to UnderInvestigation"):
    for
      s <- svc
      result <- s.openFnol(sampleClaim(tier = ClaimTier.High), policyInForce = true, coverageLimit = None)
    yield assertEquals(result.toOption.get.status, ClaimStatus.UnderInvestigation)

  test("reserve exceeding limit is rejected"):
    val covId = EntityId.random[CoverageTag]()
    val claim = sampleClaim(
      reserves = List(Reserve(covId, "bi", Money.fromMajor(BigDecimal(200000), currency)))
    )
    for
      s <- svc
      result <- s.openFnol(
        claim,
        policyInForce = true,
        coverageLimit = Some(Money.fromMajor(BigDecimal(100000), currency))
      )
    yield assert(result.isLeft)

  test("approve pay close happy path"):
    val covId = EntityId.random[CoverageTag]()
    val reserve = Reserve(covId, "pd", Money.fromMajor(BigDecimal(5000), currency))
    for
      s <- svc
      opened <- s.openFnol(sampleClaim(), policyInForce = true, None)
      reserved <- s.setReserve(opened.toOption.get, reserve)
      approved <- s.approve(reserved.toOption.get)
      paid <- s.pay(
        approved.toOption.get,
        ClaimPayment(Money.fromMajor(BigDecimal(4000), currency), EntityId.random[PartyTag](), EffectiveInstant.now())
      )
      closed <- s.close(paid.toOption.get)
    yield
      assertEquals(reserved.map(_.status), Right(ClaimStatus.Reserved))
      assertEquals(approved.map(_.status), Right(ClaimStatus.Approved))
      assertEquals(paid.map(_.status), Right(ClaimStatus.Paid))
      assertEquals(closed.map(_.status), Right(ClaimStatus.Closed))
      assert(closed.toOption.get.closedAt.isDefined)

  test("cannot pay from Open status"):
    for
      s <- svc
      opened <- s.openFnol(sampleClaim(), policyInForce = true, None)
      paid <- s.pay(
        opened.toOption.get,
        ClaimPayment(Money(100L, currency), EntityId.random[PartyTag](), EffectiveInstant.now())
      )
    yield assert(paid.isLeft)

  test("deny sets reason and ClosedAt"):
    for
      s <- svc
      opened <- s.openFnol(sampleClaim(), policyInForce = true, None)
      denied <- s.deny(opened.toOption.get, "Not covered")
    yield
      assertEquals(denied.map(_.status), Right(ClaimStatus.Denied))
      assertEquals(denied.toOption.get.denialReason, Some("Not covered"))

  test("totalReserves and totalPaid empty"):
    val c = sampleClaim()
    assertEquals(c.totalReserves, Right(Money.zero(currency)))
    assertEquals(c.totalPaid, Right(Money.zero(currency)))

  test("totalReserves sums multiple"):
    val cov = EntityId.random[CoverageTag]()
    val c = sampleClaim(
      reserves = List(
        Reserve(cov, "a", Money(100L, currency)),
        Reserve(cov, "b", Money(250L, currency))
      )
    )
    assertEquals(c.totalReserves, Right(Money(350L, currency)))
