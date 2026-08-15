package com.github.bieli.openinsuranceengine.app.bdd

import com.github.bieli.openinsuranceengine.app.{DemoDocuments, Main}
import com.github.bieli.openinsuranceengine.billing.{BillingPlan, PaymentMethod, PolicyBillingSetup}
import com.github.bieli.openinsuranceengine.claim.*
import com.github.bieli.openinsuranceengine.core.id.{ClaimTag, CoverageTag, EntityId}
import com.github.bieli.openinsuranceengine.core.money.Money
import com.github.bieli.openinsuranceengine.core.time.EffectiveInstant
import com.github.bieli.openinsuranceengine.policy.CancellationReason
import com.github.bieli.openinsuranceengine.rating.{CreditBand, InsuredProfile}
import io.cucumber.scala.{EN, ScalaDsl}

class InsuranceSteps extends ScalaDsl with EN:
  private val world = InsuranceWorld()

  Before {
    world.services = Some(world.run(Main.buildServices))
    world.period = None
    world.workflow = None
    world.claim = None
    world.lastErrors = Nil
    world.documents = Nil
    world.invoices = Nil
    world.pendingReserve = None
  }

  Given("the insurance engine is running") { () =>
    assert(world.services.isDefined)
  }

  Given("""a {int} {word} {word} with {int} km per year""") { (year: Int, make: String, model: String, km: Int) =>
    world.vehicle = world.vehicle.copy(year = year, make = make, model = model, annualMileage = Some(km))
  }

  Given("""^a Personal Auto applicant aged (\d+) licensed (\d+) years with (\d+) prior claims?, credit (\w+), region "([^"]+)"$""") {
    (age: Int, licensed: Int, claims: Int, credit: String, region: String) =>
      world.insured = InsuredProfile(
        age = age,
        yearsLicensed = licensed,
        priorClaimsLast3Years = claims,
        regionCode = region,
        creditBand = CreditBand.valueOf(credit)
      )
  }

  When("the new-business workflow runs to completion") { () =>
    world.runWorkflow()
  }

  Then("""the workflow path is {string}""") { (path: String) =>
    val actual = world.workflow.get.history.map(_.stepId).mkString(" -> ")
    assert(actual == path, s"path $actual != $path")
  }

  Then("""the policy status is {string}""") { (status: String) =>
    assertEquals(world.period.get.status.toString, status)
  }

  Then("the policy has a policy number") { () =>
    assert(world.period.get.policyNumber.exists(_.startsWith("POL-")))
  }

  Then("the submission is referred to underwriting") { () =>
    assert(world.workflow.get.state.referred)
  }

  Then("""the rated premium is {bigdecimal} PLN""") { (major: java.math.BigDecimal) =>
    val expected = Money.fromMajor(BigDecimal(major), world.currency)
    val actual = world.period.get.totalPremium
    assert(
      actual.amountMinor == expected.amountMinor && actual.currency == expected.currency,
      s"$actual != $expected"
    )
  }

  When("policy declarations are generated") { () =>
    val period = world.period.get
    val doc = world.requireDomain(
      world.run(
        world.svc.documents.render(
          DemoDocuments.PolicyDeclarationsId,
          DemoDocuments.PolicyDeclarationsInput(period, world.insured, world.vehicle)
        )
      )
    )
    world.documents = world.documents :+ doc
  }

  When("a claim acknowledgement is generated") { () =>
    val doc = world.requireDomain(
      world.run(
        world.svc.documents.render(
          DemoDocuments.ClaimAcknowledgementId,
          DemoDocuments.ClaimAcknowledgementInput(
            world.claim.get,
            world.period.get.policyNumber.getOrElse("N/A")
          )
        )
      )
    )
    world.documents = world.documents :+ doc
  }

  Then("""a {string} document named like {string} is produced""") { (docType: String, prefix: String) =>
    val found = world.documents.find(d => d.documentType.toString == docType && d.fileName.startsWith(prefix))
    assert(found.isDefined, s"No $docType starting with $prefix in ${world.documents.map(_.fileName)}")
  }

  Then("the declarations mention the rated premium") { () =>
    val body = new String(world.documents.last.content, java.nio.charset.StandardCharsets.UTF_8)
    assert(body.contains(world.period.get.totalPremium.toString), body)
  }

  When("the in-force policy is cancelled") { () =>
    val cancelled = world.run(world.svc.policy.cancel(world.period.get, CancellationReason.InsuredRequest))
    world.period = Some(cancelled.toOption.get)
  }

  Given("""the FNOL will include an initial reserve of {bigdecimal} PLN""") { (major: java.math.BigDecimal) =>
    world.pendingReserve = Some(Money.fromMajor(BigDecimal(major), world.currency))
  }

  When("""a collision FNOL is opened with tier {string} and description {string}""") {
    (tier: String, description: String) =>
      val covId = world.period.toList
        .flatMap(_.coverages)
        .headOption
        .map(_.id)
        .getOrElse(EntityId.random[CoverageTag]())
      val draft = Claim(
        id = EntityId.random[ClaimTag](),
        claimNumber = None,
        policyId = world.period.get.policyId,
        status = ClaimStatus.Draft,
        loss = LossDetails(
          lossDate = java.time.LocalDate.now(),
          lossType = LossType.Collision,
          description = description,
          location = Some("Warsaw, PL")
        ),
        claimantId = world.period.get.primaryInsuredId,
        reserves = world.pendingReserve.toList.map(amt => Reserve(covId, "vehicle_damage", amt)),
        payments = Nil,
        tier = ClaimTier.valueOf(tier),
        createdAt = EffectiveInstant.now(),
        defaultCurrency = world.currency
      )
      world.pendingReserve = None
      val inForce = world.period.exists(_.status == com.github.bieli.openinsuranceengine.policy.PolicyStatus.InForce)
      val result = world.run(world.svc.claim.openFnol(draft, inForce, Some(world.coverageLimit)))
      result match
        case Right(opened) =>
          world.lastErrors = Nil
          world.claim = Some(opened)
        case Left(errs) =>
          world.lastErrors = errs.toList.map(_.code)
          world.claim = None
  }

  When("""a vehicle_damage reserve of {bigdecimal} PLN is set""") { (major: java.math.BigDecimal) =>
    val covId = world.period.get.coverages.head.id
    val result = world.run(
      world.svc.claim.setReserve(
        world.claim.get,
        Reserve(covId, "vehicle_damage", Money.fromMajor(BigDecimal(major), world.currency))
      )
    )
    result match
      case Right(c) =>
        world.lastErrors = Nil
        world.claim = Some(c)
      case Left(errs) =>
        world.lastErrors = errs.toList.map(_.code)
  }

  When("the claim is approved") { () =>
    world.claim = Some(world.requireDomain(world.run(world.svc.claim.approve(world.claim.get))))
  }

  When("""an indemnity of {bigdecimal} PLN is paid""") { (major: java.math.BigDecimal) =>
    world.claim = Some(
      world.requireDomain(
        world.run(
          world.svc.claim.pay(
            world.claim.get,
            ClaimPayment(
              amount = Money.fromMajor(BigDecimal(major), world.currency),
              payeeId = world.period.get.primaryInsuredId,
              paidAt = EffectiveInstant.now(),
              reference = Some("IND-BDD-001")
            )
          )
        )
      )
    )
  }

  When("the claim is closed") { () =>
    world.claim = Some(world.requireDomain(world.run(world.svc.claim.close(world.claim.get))))
  }

  When("""the claim is denied because {string}""") { (reason: String) =>
    world.claim = Some(world.requireDomain(world.run(world.svc.claim.deny(world.claim.get, reason))))
  }

  Then("""the claim status is {string}""") { (status: String) =>
    assertEquals(world.claim.get.status.toString, status)
  }

  Then("the claim has a claim number") { () =>
    assert(world.claim.get.claimNumber.exists(_.startsWith("CLM-")))
  }

  Then("""total paid is {bigdecimal} PLN""") { (major: java.math.BigDecimal) =>
    val expected = Money.fromMajor(BigDecimal(major), world.currency)
    val paid = world.claim.get.totalPaid.toOption.get
    assert(
      paid.amountMinor == expected.amountMinor,
      s"$paid != $expected"
    )
  }

  Then("""the last operation failed with code {string}""") { (code: String) =>
    assert(world.lastErrors.contains(code), s"expected $code in ${world.lastErrors}")
  }

  When("quarterly invoices are created for the bound policy") { () =>
    val p = world.period.get
    world.invoices = world.requireDomain(
      world.run(
        world.svc.billing.createInvoices(
          PolicyBillingSetup(
            policyId = p.policyId,
            accountId = p.accountId,
            plan = BillingPlan.Quarterly,
            totalPremium = p.totalPremium,
            effectivePeriod = p.term.period
          )
        )
      )
    )
  }

  Then("""{int} invoices exist""") { (n: Int) =>
    assert(world.invoices.size == n, s"invoice count ${world.invoices.size} != $n")
  }

  Then("the invoices sum to the rated premium") { () =>
    val sum = world.invoices.map(_.amountDue.amountMinor).sum
    assert(sum == world.period.get.totalPremium.amountMinor, s"invoice sum $sum != ${world.period.get.totalPremium}")
  }

  When("all invoices are billed and paid in full") { () =>
    world.invoices = world.invoices.map: inv =>
      val billed = world.requireDomain(world.run(world.svc.billing.bill(inv)))
      val (paid, _) = world.requireDomain(
        world.run(world.svc.billing.applyPayment(billed, billed.amountDue, PaymentMethod.Wire))
      )
      paid
  }

  Then("""every invoice status is {string}""") { (status: String) =>
    assert(world.invoices.forall(_.status.toString == status), world.invoices.map(_.status).mkString(","))
  }

  private def assertEquals(actual: String, expected: String): Unit =
    assert(actual == expected, s"$actual != $expected")
