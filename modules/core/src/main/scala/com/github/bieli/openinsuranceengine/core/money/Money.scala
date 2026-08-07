package com.github.bieli.openinsuranceengine.core.money

import cats.Monoid

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
