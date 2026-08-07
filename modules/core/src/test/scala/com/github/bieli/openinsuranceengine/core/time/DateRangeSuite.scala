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
