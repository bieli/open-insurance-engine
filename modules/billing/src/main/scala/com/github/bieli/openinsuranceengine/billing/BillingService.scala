package com.github.bieli.openinsuranceengine.billing

import cats.effect.Sync
import cats.syntax.all.*
import com.github.bieli.openinsuranceengine.core.algebra.Repository
import com.github.bieli.openinsuranceengine.core.id.{EntityId, InvoiceId, InvoiceTag, PaymentId, PaymentTag}
import com.github.bieli.openinsuranceengine.core.money.Money
import com.github.bieli.openinsuranceengine.core.result.{DomainError, DomainResult}
import com.github.bieli.openinsuranceengine.core.time.{DateRange, EffectiveInstant}

trait BillingService[F[_]]:
  def createInvoices(setup: PolicyBillingSetup): F[DomainResult[List[Invoice]]]
  def bill(invoice: Invoice): F[DomainResult[Invoice]]
  def applyPayment(invoice: Invoice, amount: Money, method: PaymentMethod): F[DomainResult[(Invoice, Payment)]]
  def getInvoice(id: InvoiceId): F[Option[Invoice]]

object BillingService:
  def apply[F[_]: Sync](
      invoices: Repository[F, InvoiceId, Invoice],
      payments: Repository[F, PaymentId, Payment]
  ): BillingService[F] =
    new BillingService[F]:

      def createInvoices(setup: PolicyBillingSetup): F[DomainResult[List[Invoice]]] =
        Sync[F].realTimeInstant.flatMap: now =>
          val n = BillingPlan.installmentCount(setup.plan)
          if n <= 0 then
            Sync[F].pure(DomainResult.raise(DomainError.ValidationFailed("BILLING_PLAN", "Invalid installment count")))
          else
            val base = setup.totalPremium.amountMinor / n
            val remainder = setup.totalPremium.amountMinor % n
            val periodDays = setup.effectivePeriod.days
            val slice = Math.max(1L, periodDays / n)

            val created =
              (0 until n).toList.traverse: i =>
                val amount = Money(
                  base + (if i == n - 1 then remainder else 0L),
                  setup.totalPremium.currency
                )
                val start = setup.effectivePeriod.start.plusDays(i * slice)
                val end =
                  if i == n - 1 then setup.effectivePeriod.end
                  else setup.effectivePeriod.start.plusDays((i + 1) * slice - 1)
                val inv = Invoice(
                  id = EntityId.random[InvoiceTag](),
                  accountId = setup.accountId,
                  invoiceNumber = s"INV-${setup.policyId.asString.take(8)}-${i + 1}",
                  status = InvoiceStatus.Planned,
                  billingPeriod = DateRange(start, end),
                  dueDate = start,
                  items = List(
                    InvoiceItem(
                      description = s"Premium installment ${i + 1}/$n",
                      amount = amount,
                      policyId = Some(setup.policyId)
                    )
                  ),
                  amountDue = amount,
                  amountPaid = Money.zero(amount.currency),
                  createdAt = EffectiveInstant(now)
                )
                invoices.save(inv)

            created.map(Right(_))

      def bill(invoice: Invoice): F[DomainResult[Invoice]] =
        if invoice.status != InvoiceStatus.Planned then
          Sync[F].pure(
            DomainResult.raise(DomainError.Conflict("INVALID_STATUS", s"Cannot bill from ${invoice.status}"))
          )
        else invoices.save(invoice.copy(status = InvoiceStatus.Billed)).map(Right(_))

      def applyPayment(
          invoice: Invoice,
          amount: Money,
          method: PaymentMethod
      ): F[DomainResult[(Invoice, Payment)]] =
        if invoice.status == InvoiceStatus.Paid || invoice.status == InvoiceStatus.Voided then
          Sync[F].pure(
            DomainResult.raise(DomainError.Conflict("INVALID_STATUS", s"Cannot pay invoice in status ${invoice.status}"))
          )
        else if amount.currency != invoice.amountDue.currency then
          Sync[F].pure(
            DomainResult.raise(DomainError.ValidationFailed("CURRENCY", "Payment currency mismatch"))
          )
        else
          Sync[F].realTimeInstant.flatMap: now =>
            (invoice.amountPaid + amount) match
              case Left(err) => Sync[F].pure(DomainResult.raise(DomainError.Unexpected("MONEY", err)))
              case Right(newPaid) =>
                val newStatus =
                  if newPaid.amountMinor >= invoice.amountDue.amountMinor then InvoiceStatus.Paid
                  else if newPaid.amountMinor > 0 then InvoiceStatus.PartiallyPaid
                  else invoice.status
                val payment = Payment(
                  id = EntityId.random[PaymentTag](),
                  invoiceId = invoice.id,
                  accountId = invoice.accountId,
                  amount = amount,
                  method = method,
                  status = PaymentStatus.Cleared,
                  receivedAt = EffectiveInstant(now)
                )
                for
                  savedPay <- payments.save(payment)
                  savedInv <- invoices.save(invoice.copy(amountPaid = newPaid, status = newStatus))
                yield Right((savedInv, savedPay))

      def getInvoice(id: InvoiceId): F[Option[Invoice]] = invoices.get(id)
