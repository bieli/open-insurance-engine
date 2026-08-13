package com.github.bieli.openinsuranceengine.billing

import com.github.bieli.openinsuranceengine.core.id.{AccountId, InvoiceId, PaymentId, PolicyId}
import com.github.bieli.openinsuranceengine.core.money.Money
import com.github.bieli.openinsuranceengine.core.time.{DateRange, EffectiveInstant}

/** BillingCenter-style invoice / payment domain. */
enum InvoiceStatus:
  case Planned, Billed, Due, Paid, PartiallyPaid, WrittenOff, Voided

object InvoiceStatus:
  given CanEqual[InvoiceStatus, InvoiceStatus] = CanEqual.derived

enum PaymentStatus:
  case Pending, Cleared, Failed, Reversed

object PaymentStatus:
  given CanEqual[PaymentStatus, PaymentStatus] = CanEqual.derived

enum PaymentMethod:
  case DirectDebit, CreditCard, Wire, Check, Cash
  case External(code: String)

object PaymentMethod:
  given CanEqual[PaymentMethod, PaymentMethod] = CanEqual.derived

enum BillingPlan:
  case Monthly, Quarterly, SemiAnnual, Annual
  case Custom(installments: Int)

object BillingPlan:
  given CanEqual[BillingPlan, BillingPlan] = CanEqual.derived

  def installmentCount(plan: BillingPlan): Int = plan match
    case BillingPlan.Monthly          => 12
    case BillingPlan.Quarterly        => 4
    case BillingPlan.SemiAnnual       => 2
    case BillingPlan.Annual           => 1
    case BillingPlan.Custom(n)        => n

final case class InvoiceItem(
    description: String,
    amount: Money,
    policyId: Option[PolicyId] = None
)

final case class Invoice(
    id: InvoiceId,
    accountId: AccountId,
    invoiceNumber: String,
    status: InvoiceStatus,
    billingPeriod: DateRange,
    dueDate: java.time.LocalDate,
    items: List[InvoiceItem],
    amountDue: Money,
    amountPaid: Money,
    createdAt: EffectiveInstant
):
  def remaining: Either[String, Money] = amountDue - amountPaid

final case class Payment(
    id: PaymentId,
    invoiceId: InvoiceId,
    accountId: AccountId,
    amount: Money,
    method: PaymentMethod,
    status: PaymentStatus,
    receivedAt: EffectiveInstant,
    reference: Option[String] = None
)

final case class PolicyBillingSetup(
    policyId: PolicyId,
    accountId: AccountId,
    plan: BillingPlan,
    totalPremium: Money,
    effectivePeriod: DateRange
)
