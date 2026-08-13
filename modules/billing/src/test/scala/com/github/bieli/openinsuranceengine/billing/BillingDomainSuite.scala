package com.github.bieli.openinsuranceengine.billing

import com.github.bieli.openinsuranceengine.core.id.{AccountTag, EntityId, InvoiceTag, PaymentTag, PolicyTag}
import com.github.bieli.openinsuranceengine.core.money.{CurrencyCode, Money}
import com.github.bieli.openinsuranceengine.core.time.{DateRange, EffectiveInstant}
import munit.FunSuite

import java.time.LocalDate

/** Billing domain enums, Invoice.remaining, payment method edge cases. */
class BillingDomainSuite extends FunSuite:
  private val pln = CurrencyCode.PLN

  private def invoice(due: Long, paid: Long, status: InvoiceStatus = InvoiceStatus.Billed): Invoice =
    Invoice(
      id = EntityId.random[InvoiceTag](),
      accountId = EntityId.random[AccountTag](),
      invoiceNumber = "INV-1",
      status = status,
      billingPeriod = DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31)),
      dueDate = LocalDate.of(2026, 1, 15),
      items = List(
        InvoiceItem("Premium", Money(due, pln), Some(EntityId.random[PolicyTag]()))
      ),
      amountDue = Money(due, pln),
      amountPaid = Money(paid, pln),
      createdAt = EffectiveInstant.now()
    )

  test("Invoice.remaining is due minus paid"):
    assertEquals(invoice(100_00L, 40_00L).remaining, Right(Money(60_00L, pln)))

  test("Invoice.remaining is zero when fully paid"):
    assertEquals(invoice(100_00L, 100_00L, InvoiceStatus.Paid).remaining, Right(Money.zero(pln)))

  test("Invoice.remaining can be negative if overpaid (cash application edge)"):
    assertEquals(invoice(100_00L, 110_00L).remaining, Right(Money(-10_00L, pln)))

  test("PaymentMethod External is distinguished by gateway code"):
    assert(PaymentMethod.External("PAYU") != PaymentMethod.External("STRIPE"))
    assert(PaymentMethod.Wire != PaymentMethod.DirectDebit)

  test("PaymentStatus lifecycle values are distinct"):
    assert(PaymentStatus.Pending != PaymentStatus.Cleared)
    assert(PaymentStatus.Failed != PaymentStatus.Reversed)

  test("InvoiceStatus includes write-off and void"):
    assert(InvoiceStatus.WrittenOff != InvoiceStatus.Voided)
    assert(InvoiceStatus.Due != InvoiceStatus.Billed)

  test("Payment record retains optional bank reference"):
    val p = Payment(
      id = EntityId.random[PaymentTag](),
      invoiceId = EntityId.random[InvoiceTag](),
      accountId = EntityId.random[AccountTag](),
      amount = Money(250_00L, pln),
      method = PaymentMethod.External("BLIK"),
      status = PaymentStatus.Cleared,
      receivedAt = EffectiveInstant.now(),
      reference = Some("TXN-998877")
    )
    assertEquals(p.reference, Some("TXN-998877"))
    assertEquals(p.method, PaymentMethod.External("BLIK"))

  test("PolicyBillingSetup binds plan to policy term"):
    val setup = PolicyBillingSetup(
      policyId = EntityId.random[PolicyTag](),
      accountId = EntityId.random[AccountTag](),
      plan = BillingPlan.Custom(6),
      totalPremium = Money.fromMajor(BigDecimal(1800), pln),
      effectivePeriod = DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))
    )
    assertEquals(BillingPlan.installmentCount(setup.plan), 6)
