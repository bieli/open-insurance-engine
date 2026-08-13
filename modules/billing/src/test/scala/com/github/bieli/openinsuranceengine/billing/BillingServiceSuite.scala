package com.github.bieli.openinsuranceengine.billing

import cats.effect.IO
import com.github.bieli.openinsuranceengine.core.algebra.Repository
import com.github.bieli.openinsuranceengine.core.id.{
  AccountTag,
  EntityId,
  InvoiceId,
  InvoiceTag,
  PaymentId,
  PolicyTag
}
import com.github.bieli.openinsuranceengine.core.money.{CurrencyCode, Money}
import com.github.bieli.openinsuranceengine.core.time.DateRange
import munit.CatsEffectSuite

import java.time.LocalDate

class BillingServiceSuite extends CatsEffectSuite:
  private val currency = CurrencyCode.PLN
  private val today = LocalDate.now()

  private def setup(plan: BillingPlan, premiumMinor: Long = 120_00L): PolicyBillingSetup =
    PolicyBillingSetup(
      policyId = EntityId.random[PolicyTag](),
      accountId = EntityId.random[AccountTag](),
      plan = plan,
      totalPremium = Money(premiumMinor, currency),
      effectivePeriod = DateRange(today, today.plusYears(1))
    )

  private def services =
    for
      inv <- Repository.inMemory[IO, InvoiceId, Invoice](_.id)
      pay <- Repository.inMemory[IO, PaymentId, Payment](_.id)
    yield BillingService[IO](inv, pay)

  test("quarterly creates 4 invoices summing to premium"):
    for
      svc <- services
      result <- svc.createInvoices(setup(BillingPlan.Quarterly, 100_00L))
    yield
      assert(result.isRight)
      val invoices = result.toOption.get
      assertEquals(invoices.size, 4)
      assertEquals(invoices.map(_.amountDue.amountMinor).sum, 100_00L)
      assert(invoices.forall(_.status == InvoiceStatus.Planned))

  test("remainder goes to last installment"):
    for
      svc <- services
      // 10001 / 3 = 3333 rem 2 -> last gets +2
      result <- svc.createInvoices(setup(BillingPlan.Custom(3), 100_01L))
    yield
      val amounts = result.toOption.get.map(_.amountDue.amountMinor)
      assertEquals(amounts.init.toSet, Set(3333L))
      assertEquals(amounts.last, 3335L)
      assertEquals(amounts.sum, 100_01L)

  test("bill transitions Planned to Billed"):
    for
      svc <- services
      created <- svc.createInvoices(setup(BillingPlan.Annual))
      billed <- svc.bill(created.toOption.get.head)
    yield assertEquals(billed.map(_.status), Right(InvoiceStatus.Billed))

  test("cannot bill already billed invoice"):
    for
      svc <- services
      created <- svc.createInvoices(setup(BillingPlan.Annual))
      billed <- svc.bill(created.toOption.get.head)
      again <- svc.bill(billed.toOption.get)
    yield assert(again.isLeft)

  test("full payment marks Paid"):
    for
      svc <- services
      created <- svc.createInvoices(setup(BillingPlan.Annual, 500_00L))
      billed <- svc.bill(created.toOption.get.head)
      paid <- svc.applyPayment(billed.toOption.get, Money(500_00L, currency), PaymentMethod.Wire)
    yield
      assertEquals(paid.map(_._1.status), Right(InvoiceStatus.Paid))
      assertEquals(paid.map(_._2.status), Right(PaymentStatus.Cleared))

  test("partial payment marks PartiallyPaid"):
    for
      svc <- services
      created <- svc.createInvoices(setup(BillingPlan.Annual, 500_00L))
      billed <- svc.bill(created.toOption.get.head)
      paid <- svc.applyPayment(billed.toOption.get, Money(100_00L, currency), PaymentMethod.Cash)
    yield assertEquals(paid.map(_._1.status), Right(InvoiceStatus.PartiallyPaid))

  test("reject payment with currency mismatch"):
    for
      svc <- services
      created <- svc.createInvoices(setup(BillingPlan.Annual))
      billed <- svc.bill(created.toOption.get.head)
      result <- svc.applyPayment(billed.toOption.get, Money(100L, CurrencyCode.EUR), PaymentMethod.Cash)
    yield assert(result.isLeft)

  test("reject payment on already Paid invoice"):
    for
      svc <- services
      created <- svc.createInvoices(setup(BillingPlan.Annual, 100_00L))
      billed <- svc.bill(created.toOption.get.head)
      paid <- svc.applyPayment(billed.toOption.get, Money(100_00L, currency), PaymentMethod.DirectDebit)
      again <- svc.applyPayment(paid.toOption.get._1, Money(10L, currency), PaymentMethod.Cash)
    yield assert(again.isLeft)

  test("custom plan with zero installments fails"):
    for
      svc <- services
      result <- svc.createInvoices(setup(BillingPlan.Custom(0)))
    yield assert(result.isLeft)

  test("BillingPlan.installmentCount"):
    assertEquals(BillingPlan.installmentCount(BillingPlan.Monthly), 12)
    assertEquals(BillingPlan.installmentCount(BillingPlan.Quarterly), 4)
    assertEquals(BillingPlan.installmentCount(BillingPlan.SemiAnnual), 2)
    assertEquals(BillingPlan.installmentCount(BillingPlan.Annual), 1)
    assertEquals(BillingPlan.installmentCount(BillingPlan.Custom(7)), 7)

  test("getInvoice returns saved invoice and None for unknown id"):
    for
      svc <- services
      created <- svc.createInvoices(setup(BillingPlan.Annual, 100_00L))
      inv = created.toOption.get.head
      found <- svc.getInvoice(inv.id)
      missing <- svc.getInvoice(EntityId.random[InvoiceTag]())
    yield
      assertEquals(found.map(_.id), Some(inv.id))
      assertEquals(missing, None)

  test("InvoiceItem default policyId is None"):
    val item = InvoiceItem("misc fee", Money(50_00L, currency))
    assertEquals(item.policyId, None)
