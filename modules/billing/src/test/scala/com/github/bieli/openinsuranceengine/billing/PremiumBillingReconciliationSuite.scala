package com.github.bieli.openinsuranceengine.billing

import cats.effect.IO
import cats.syntax.all.*
import com.github.bieli.openinsuranceengine.core.algebra.Repository
import com.github.bieli.openinsuranceengine.core.id.{
  AccountTag,
  EntityId,
  InvoiceId,
  PaymentId,
  PolicyTag
}
import com.github.bieli.openinsuranceengine.core.money.{CurrencyCode, Money}
import com.github.bieli.openinsuranceengine.core.time.DateRange
import munit.CatsEffectSuite

import java.time.LocalDate

/**
 * BillingCenter-style controls: written premium must equal billed installments;
 * cash application must reach Paid without over/under residual.
 */
class PremiumBillingReconciliationSuite extends CatsEffectSuite:
  private val pln = CurrencyCode.PLN
  private val termStart = LocalDate.of(2026, 1, 1)
  private val termEnd = LocalDate.of(2026, 12, 31)

  private def svc =
    for
      inv <- Repository.inMemory[IO, InvoiceId, Invoice](_.id)
      pay <- Repository.inMemory[IO, PaymentId, Payment](_.id)
    yield BillingService[IO](inv, pay)

  private def setup(plan: BillingPlan, premium: Money): PolicyBillingSetup =
    PolicyBillingSetup(
      policyId = EntityId.random[PolicyTag](),
      accountId = EntityId.random[AccountTag](),
      plan = plan,
      totalPremium = premium,
      effectivePeriod = DateRange(termStart, termEnd)
    )

  test("written premium equals sum of monthly invoices (control total)"):
    val written = Money.fromMajor(BigDecimal("3599.97"), pln) // awkward cents
    for
      s <- svc
      created <- s.createInvoices(setup(BillingPlan.Monthly, written))
    yield
      val invoices = created.toOption.get
      assertEquals(invoices.size, 12)
      assertEquals(invoices.map(_.amountDue.amountMinor).sum, written.amountMinor)
      // first 11 equal; last absorbs remainder
      assertEquals(invoices.init.map(_.amountDue.amountMinor).distinct.size, 1)

  test("pay-all installments leaves account with zero outstanding"):
    val written = Money.fromMajor(BigDecimal(2400), pln)
    for
      s <- svc
      created <- s.createInvoices(setup(BillingPlan.Quarterly, written))
      billed <- created.toOption.get.traverse(s.bill)
      paid <- billed.traverse: invEither =>
        val inv = invEither.toOption.get
        s.applyPayment(inv, inv.amountDue, PaymentMethod.DirectDebit)
    yield
      assert(paid.forall(_.isRight))
      assert(paid.forall(_.toOption.get._1.status == InvoiceStatus.Paid))
      val collected = paid.map(_.toOption.get._2.amount.amountMinor).sum
      assertEquals(collected, written.amountMinor)

  test("two partial DirectDebit hits then final Wire clears invoice"):
    for
      s <- svc
      created <- s.createInvoices(setup(BillingPlan.Annual, Money.fromMajor(BigDecimal(1000), pln)))
      billed <- s.bill(created.toOption.get.head)
      inv0 = billed.toOption.get
      p1 <- s.applyPayment(inv0, Money.fromMajor(BigDecimal(300), pln), PaymentMethod.DirectDebit)
      p2 <- s.applyPayment(p1.toOption.get._1, Money.fromMajor(BigDecimal(400), pln), PaymentMethod.DirectDebit)
      p3 <- s.applyPayment(p2.toOption.get._1, Money.fromMajor(BigDecimal(300), pln), PaymentMethod.Wire)
    yield
      assertEquals(p1.map(_._1.status), Right(InvoiceStatus.PartiallyPaid))
      assertEquals(p2.map(_._1.status), Right(InvoiceStatus.PartiallyPaid))
      assertEquals(p3.map(_._1.status), Right(InvoiceStatus.Paid))
      assertEquals(p3.toOption.get._1.amountPaid, Money.fromMajor(BigDecimal(1000), pln))

  test("billing periods cover full policy term without gaps at endpoints"):
    for
      s <- svc
      created <- s.createInvoices(setup(BillingPlan.SemiAnnual, Money.fromMajor(BigDecimal(1800), pln)))
    yield
      val invoices = created.toOption.get
      assertEquals(invoices.size, 2)
      assertEquals(invoices.head.billingPeriod.start, termStart)
      assertEquals(invoices.last.billingPeriod.end, termEnd)
      assert(invoices.head.billingPeriod.overlaps(invoices.last.billingPeriod) ||
        !invoices.head.billingPeriod.end.isBefore(invoices.last.billingPeriod.start.minusDays(1)))
