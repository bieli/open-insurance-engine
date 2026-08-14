package com.github.bieli.openinsuranceengine.claim

import cats.effect.IO
import com.github.bieli.openinsuranceengine.core.algebra.Repository
import com.github.bieli.openinsuranceengine.core.id.{ClaimId, ClaimTag, EntityId, PartyTag, PolicyTag}
import com.github.bieli.openinsuranceengine.core.time.EffectiveInstant
import munit.CatsEffectSuite

import java.time.LocalDate

/** Additional ClaimService status-transition edge cases. */
class ClaimLifecycleEdgeSuite extends CatsEffectSuite:

  private def draftClaim: Claim =
    Claim(
      id = EntityId.random[ClaimTag](),
      claimNumber = None,
      policyId = EntityId.random[PolicyTag](),
      status = ClaimStatus.Draft,
      loss = LossDetails(LocalDate.now().minusDays(2), LossType.PropertyDamage, "Fence damage"),
      claimantId = EntityId.random[PartyTag](),
      reserves = Nil,
      payments = Nil,
      tier = ClaimTier.Low,
      createdAt = EffectiveInstant.now()
    )

  private def svc =
    Repository.inMemory[IO, ClaimId, Claim](_.id).map(ClaimService[IO](_))

  test("cannot approve from Draft without opening FNOL"):
    for
      s <- svc
      result <- s.approve(draftClaim)
    yield assert(result.isLeft)

  test("get returns persisted claim after FNOL"):
    for
      s <- svc
      opened <- s.openFnol(draftClaim, policyInForce = true, None)
      got <- s.get(opened.toOption.get.id)
      missing <- s.get(EntityId.random[ClaimTag]())
    yield
      assert(got.isDefined)
      assertEquals(missing, None)

  test("approve allowed from Open after FNOL"):
    for
      s <- svc
      opened <- s.openFnol(draftClaim, policyInForce = true, None)
      approved <- s.approve(opened.toOption.get)
    yield assertEquals(approved.map(_.status), Right(ClaimStatus.Approved))

  test("loss date today is accepted (boundary)"):
    val today = draftClaim.copy(loss = draftClaim.loss.copy(lossDate = LocalDate.now()))
    for
      s <- svc
      result <- s.openFnol(today, policyInForce = true, None)
    yield assert(result.isRight)
