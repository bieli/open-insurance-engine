package com.github.bieli.openinsuranceengine.policy

import cats.effect.IO
import com.github.bieli.openinsuranceengine.core.algebra.Repository
import com.github.bieli.openinsuranceengine.core.id.*
import com.github.bieli.openinsuranceengine.core.money.{CurrencyCode, Money}
import com.github.bieli.openinsuranceengine.core.product.*
import com.github.bieli.openinsuranceengine.core.risk.VehicleRisk
import com.github.bieli.openinsuranceengine.core.time.{DateRange, EffectiveInstant}
import munit.CatsEffectSuite

import java.time.LocalDate

class PolicyServiceSuite extends CatsEffectSuite:
  private val currency = CurrencyCode.PLN

  private def samplePeriod: PolicyPeriod =
    val cov = Coverage(
      id = EntityId.random[CoverageTag](),
      code = "BI",
      coverageType = CoverageType.BodilyInjury,
      limit = Money.fromMajor(BigDecimal(500000), currency),
      deductible = Money.fromMajor(BigDecimal(500), currency),
      premium = Money.fromMajor(BigDecimal(900), currency)
    )
    PolicyPeriod(
      policyId = EntityId.random[PolicyTag](),
      accountId = EntityId.random[AccountTag](),
      productId = EntityId.random[ProductTag](),
      policyNumber = None,
      status = PolicyStatus.Draft,
      jobType = JobType.Submission,
      term = PolicyTerm(DateRange(LocalDate.now(), LocalDate.now().plusYears(1)), 1),
      lineOfBusiness = LineOfBusiness.PersonalAuto,
      primaryInsuredId = EntityId.random[PartyTag](),
      coverages = List(cov),
      risks = List(VehicleRisk("VIN12345678901234", "Toyota", "Corolla", 2019)),
      totalPremium = cov.premium,
      createdAt = EffectiveInstant.now()
    )

  test("draft -> quote -> bind happy path"):
    for
      repo <- Repository.inMemory[IO, PolicyId, PolicyPeriod](_.policyId)
      svc = PolicyService[IO](repo)
      draft <- svc.createDraft(samplePeriod)
      _ = assert(draft.isRight)
      quoted <- svc.quote(draft.toOption.get)
      _ = assertEquals(quoted.map(_.status), Right(PolicyStatus.Quoted))
      bound <- svc.bind(quoted.toOption.get, "POL-TEST-1")
    yield assertEquals(bound.map(_.status), Right(PolicyStatus.InForce))

  test("reject auto policy without vehicle"):
    val bad = samplePeriod.copy(risks = Nil)
    for
      repo <- Repository.inMemory[IO, PolicyId, PolicyPeriod](_.policyId)
      svc = PolicyService[IO](repo)
      result <- svc.createDraft(bad)
    yield assert(result.isLeft)

  test("reject draft with no coverages"):
    for
      repo <- Repository.inMemory[IO, PolicyId, PolicyPeriod](_.policyId)
      svc = PolicyService[IO](repo)
      result <- svc.createDraft(samplePeriod.copy(coverages = Nil, totalPremium = Money.zero(currency)))
    yield assert(result.isLeft)

  test("cannot quote from InForce"):
    for
      repo <- Repository.inMemory[IO, PolicyId, PolicyPeriod](_.policyId)
      svc = PolicyService[IO](repo)
      draft <- svc.createDraft(samplePeriod)
      quoted <- svc.quote(draft.toOption.get)
      bound <- svc.bind(quoted.toOption.get, "POL-1")
      again <- svc.quote(bound.toOption.get)
    yield assert(again.isLeft)

  test("cannot bind from Cancelled"):
    for
      repo <- Repository.inMemory[IO, PolicyId, PolicyPeriod](_.policyId)
      svc = PolicyService[IO](repo)
      draft <- svc.createDraft(samplePeriod)
      quoted <- svc.quote(draft.toOption.get)
      bound <- svc.bind(quoted.toOption.get, "POL-2")
      cancelled <- svc.cancel(bound.toOption.get, CancellationReason.InsuredRequest)
      bindAgain <- svc.bind(cancelled.toOption.get, "POL-2B")
    yield assert(bindAgain.isLeft)

  test("cancel in-force policy"):
    for
      repo <- Repository.inMemory[IO, PolicyId, PolicyPeriod](_.policyId)
      svc = PolicyService[IO](repo)
      draft <- svc.createDraft(samplePeriod)
      quoted <- svc.quote(draft.toOption.get)
      bound <- svc.bind(quoted.toOption.get, "POL-3")
      cancelled <- svc.cancel(bound.toOption.get, CancellationReason.NonPayment)
    yield assertEquals(cancelled.map(_.status), Right(PolicyStatus.Cancelled))

  test("cannot cancel draft"):
    for
      repo <- Repository.inMemory[IO, PolicyId, PolicyPeriod](_.policyId)
      svc = PolicyService[IO](repo)
      draft <- svc.createDraft(samplePeriod)
      cancelled <- svc.cancel(draft.toOption.get, CancellationReason.InsuredRequest)
    yield assert(cancelled.isLeft)

  test("recalculatePremium sums coverages"):
    val p = samplePeriod
    val extra = p.coverages.head.copy(
      id = EntityId.random[CoverageTag](),
      code = "COLL",
      premium = Money.fromMajor(BigDecimal(300), currency)
    )
    val recalculated = p.withCoverage(extra).recalculatePremium
    assertEquals(recalculated.map(_.totalPremium), Right(Money.fromMajor(BigDecimal(1200), currency)))

  test("recalculatePremium empty coverages zeros out"):
    val p = samplePeriod.copy(coverages = Nil)
    assertEquals(p.recalculatePremium.map(_.totalPremium), Right(Money.zero(currency)))

  test("get returns saved policy"):
    for
      repo <- Repository.inMemory[IO, PolicyId, PolicyPeriod](_.policyId)
      svc = PolicyService[IO](repo)
      draft <- svc.createDraft(samplePeriod)
      got <- svc.get(draft.toOption.get.policyId)
      missing <- svc.get(EntityId.random[PolicyTag]())
    yield
      assert(got.isDefined)
      assertEquals(missing, None)

  test("underwriting referral blocks bind for young driver context via rule set"):
    val uw = PolicyRules.UnderwritingContext(samplePeriod, driverAge = Some(18))
    val result = PolicyRules.personalAutoRuleSet.evaluate(uw)
    assert(result.isRight)
    assert(result.toOption.get.referrals.nonEmpty)
