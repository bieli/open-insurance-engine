package com.github.bieli.openinsuranceengine.core.money

/** ISO-4217 currency code as opaque type. */
opaque type CurrencyCode = String

object CurrencyCode:
  def apply(code: String): Either[String, CurrencyCode] =
    if code.length == 3 && code.forall(_.isUpper) then Right(code)
    else Left(s"Invalid currency code: $code")

  def unsafe(code: String): CurrencyCode = code
  val PLN: CurrencyCode = "PLN"
  val EUR: CurrencyCode = "EUR"
  val USD: CurrencyCode = "USD"
  val GBP: CurrencyCode = "GBP"

  extension (c: CurrencyCode) def value: String = c
  given CanEqual[CurrencyCode, CurrencyCode] = CanEqual.derived

/**
 * Money represented in minor units (grosze / cents) to avoid floating-point
 * arithmetic - standard practice in financial / insurance cores.
 */
final case class Money(amountMinor: Long, currency: CurrencyCode):
  def +(other: Money): Either[String, Money] =
    if currency == other.currency then Right(Money(amountMinor + other.amountMinor, currency))
    else Left(s"Currency mismatch: ${currency.value} vs ${other.currency.value}")

  def -(other: Money): Either[String, Money] =
    if currency == other.currency then Right(Money(amountMinor - other.amountMinor, currency))
    else Left(s"Currency mismatch: ${currency.value} vs ${other.currency.value}")

  def *(factor: BigDecimal): Money =
    Money((BigDecimal(amountMinor) * factor).setScale(0, BigDecimal.RoundingMode.HALF_UP).toLong, currency)

  def isPositive: Boolean = amountMinor > 0
  def isZero: Boolean = amountMinor == 0
  def negate: Money = Money(-amountMinor, currency)

  def toMajor: BigDecimal = BigDecimal(amountMinor) / 100

  override def toString: String = f"${toMajor}%1.2f ${currency.value}"
