package com.github.bieli.openinsuranceengine.core.time

import java.time.{Instant, LocalDate}
import munit.FunSuite

class DateRangeSuite extends FunSuite:
  private val jan1 = LocalDate.of(2026, 1, 1)
  private val jan31 = LocalDate.of(2026, 1, 31)
  private val feb1 = LocalDate.of(2026, 2, 1)
  private val dec31 = LocalDate.of(2026, 12, 31)

  test("single-day range has one day"):
    val r = DateRange(jan1, jan1)
    assertEquals(r.days, 1L)
    assert(r.contains(jan1))

  test("full year inclusive days"):
    assertEquals(DateRange(jan1, dec31).days, 365L)

  test("contains inclusive boundaries"):
    val r = DateRange(jan1, jan31)
    assert(r.contains(jan1))
    assert(r.contains(jan31))
    assert(r.contains(LocalDate.of(2026, 1, 15)))
    assert(!r.contains(feb1))
    assert(!r.contains(LocalDate.of(2025, 12, 31)))

  test("overlaps adjacent and nested"):
    val a = DateRange(jan1, jan31)
    val b = DateRange(jan31, feb1)
    val c = DateRange(feb1, LocalDate.of(2026, 2, 28))
    val d = DateRange(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 20))
    assert(a.overlaps(b))
    assert(!a.overlaps(c))
    assert(a.overlaps(d))
    assert(d.overlaps(a))

  test("rejects end before start"):
    intercept[IllegalArgumentException]:
      DateRange(feb1, jan1)

  test("EffectiveInstant toLocalDate uses UTC"):
    val instant = Instant.parse("2026-06-15T23:30:00Z")
    val eff = EffectiveInstant(instant)
    assertEquals(eff.toLocalDate, LocalDate.of(2026, 6, 15))
    assertEquals(eff.value, instant)
