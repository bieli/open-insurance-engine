package com.github.bieli.openinsuranceengine.rules

import munit.FunSuite

class RuleCatalogSuite extends FunSuite:
  test("loads oie-rules.yaml from the classpath"):
    val doc = RuleCatalog.document
    assertEquals(doc.underwriting.id, "personal-auto-uw")
    assertEquals(doc.fnol.id, "RS_FNOL")
    assert(doc.underwriting.rules.exists(_.id == "young-driver"))
    assert(doc.fnol.rules.exists(_.id == "CLM_POLICY_INFORCE"))
    assert(doc.claimValidation.exists(_.check == "nonBlank"))
    assert(doc.rating.plans.exists(_.id == "PA-WEIGHTED-PL-2026"))
    assert(doc.rating.tables.exists(_.id == "age"))

  test("compiles YAML underwriting: age 18 is referred, age 23 is not"):
    final case class Uw(driverAge: Int)
    val rules = RuleCatalog.compile[Uw](
      RuleCatalog.document.underwriting,
      ctx => Map("driverAge" -> Fact.num(ctx.driverAge))
    )
    val young = rules.evaluate(Uw(18))
    val adult = rules.evaluate(Uw(23))
    assert(young.isRight)
    assert(young.toOption.get.referrals.nonEmpty)
    assert(adult.isRight)
    assert(adult.toOption.get.referrals.isEmpty)

  test("compiles YAML FNOL: policy not in force is rejected"):
    final case class Fnol(policyInForce: Boolean, tier: String, reserves: Option[BigDecimal], limit: Option[BigDecimal])
    val rules = RuleCatalog.compile[Fnol](
      RuleCatalog.document.fnol,
      ctx =>
        Map(
          "policyInForce" -> Fact.Bool(ctx.policyInForce),
          "tier" -> Fact.Text(ctx.tier),
          "totalReserves" -> Fact.fromOptionBig(ctx.reserves),
          "coverageLimit" -> Fact.fromOptionBig(ctx.limit)
        )
    )
    val denied = rules.evaluate(Fnol(policyInForce = false, "Medium", None, None), stopOnReject = true)
    assert(denied.isLeft)

    val high = rules.evaluate(Fnol(policyInForce = true, "High", None, None))
    assert(high.isRight)
    assert(high.toOption.get.referrals.nonEmpty)

    val overLimit = rules.evaluate(
      Fnol(policyInForce = true, "Low", Some(BigDecimal(100)), Some(BigDecimal(50))),
      stopOnReject = true
    )
    assert(overLimit.isLeft)
