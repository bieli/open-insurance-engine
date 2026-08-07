package com.github.bieli.openinsuranceengine.core.money

import munit.FunSuite

class MoneySuite extends FunSuite:
  test("add same currency"):
    val a = Money.fromMajor(BigDecimal(10), CurrencyCode.PLN)
    val b = Money.fromMajor(BigDecimal(2.50), CurrencyCode.PLN)
    assertEquals(a + b, Right(Money(1250L, CurrencyCode.PLN)))

  test("reject currency mismatch on add"):
    val a = Money.fromMajor(BigDecimal(10), CurrencyCode.PLN)
    val b = Money.fromMajor(BigDecimal(10), CurrencyCode.EUR)
    assert((a + b).isLeft)

  test("subtract same currency"):
    val a = Money.fromMajor(BigDecimal(10), CurrencyCode.PLN)
    val b = Money.fromMajor(BigDecimal(2.50), CurrencyCode.PLN)
    assertEquals(a - b, Right(Money(750L, CurrencyCode.PLN)))

  test("reject currency mismatch on subtract"):
    val a = Money(100L, CurrencyCode.USD)
    val b = Money(50L, CurrencyCode.GBP)
    assert((a - b).isLeft)

  test("subtract to negative amount"):
    val a = Money(50L, CurrencyCode.PLN)
    val b = Money(100L, CurrencyCode.PLN)
    assertEquals(a - b, Right(Money(-50L, CurrencyCode.PLN)))

  test("multiply rounds half-up"):
    val a = Money(100L, CurrencyCode.PLN)
    assertEquals(a * BigDecimal("1.5"), Money(150L, CurrencyCode.PLN))

  test("multiply rounds 0.5 up"):
    // 101 * 0.5 = 50.5 -> 51
    assertEquals(Money(101L, CurrencyCode.PLN) * BigDecimal("0.5"), Money(51L, CurrencyCode.PLN))

  test("multiply by zero"):
    assertEquals(Money(999L, CurrencyCode.EUR) * BigDecimal(0), Money.zero(CurrencyCode.EUR))

  test("zero helpers"):
    val z = Money.zero(CurrencyCode.PLN)
    assert(z.isZero)
    assert(!z.isPositive)
    assertEquals(z.amountMinor, 0L)

  test("positive and negate"):
    val m = Money(250L, CurrencyCode.PLN)
    assert(m.isPositive)
    assertEquals(m.negate, Money(-250L, CurrencyCode.PLN))
    assert(!m.negate.isPositive)

  test("fromMajor rounds half-up"):
    assertEquals(Money.fromMajor(BigDecimal("10.005"), CurrencyCode.PLN), Money(1001L, CurrencyCode.PLN))
    assertEquals(Money.fromMajor(BigDecimal("10.004"), CurrencyCode.PLN), Money(1000L, CurrencyCode.PLN))

  test("toMajor conversion"):
    assertEquals(Money(12345L, CurrencyCode.PLN).toMajor, BigDecimal("123.45"))

  test("monoid combines same currency"):
    val M = Money.monoid(CurrencyCode.PLN)
    assertEquals(M.empty, Money.zero(CurrencyCode.PLN))
    assertEquals(M.combine(Money(100L, CurrencyCode.PLN), Money(50L, CurrencyCode.PLN)), Money(150L, CurrencyCode.PLN))

  test("monoid throws on currency mismatch"):
    val M = Money.monoid(CurrencyCode.PLN)
    intercept[IllegalArgumentException]:
      M.combine(Money(100L, CurrencyCode.PLN), Money(50L, CurrencyCode.EUR))

  test("CurrencyCode validation"):
    assert(CurrencyCode("PLN").isRight)
    assert(CurrencyCode("pln").isLeft)
    assert(CurrencyCode("EURO").isLeft)
    assert(CurrencyCode("PL").isLeft)
    assert(CurrencyCode("").isLeft)
