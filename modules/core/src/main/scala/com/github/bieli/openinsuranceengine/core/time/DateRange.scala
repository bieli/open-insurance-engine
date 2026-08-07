package com.github.bieli.openinsuranceengine.core.time

import java.time.{Instant, LocalDate, ZoneOffset}

/** Inclusive date range used for policy terms, billing periods, claim loss dates. */
final case class DateRange(start: LocalDate, end: LocalDate):
  require(!end.isBefore(start), s"DateRange end ($end) before start ($start)")

  def contains(date: LocalDate): Boolean =
    !date.isBefore(start) && !date.isAfter(end)

  def overlaps(other: DateRange): Boolean =
    !start.isAfter(other.end) && !other.start.isAfter(end)

  def days: Long = end.toEpochDay - start.toEpochDay + 1

object DateRange:
  given CanEqual[DateRange, DateRange] = CanEqual.derived

opaque type EffectiveInstant = Instant

object EffectiveInstant:
  def apply(i: Instant): EffectiveInstant = i
  def now(): EffectiveInstant = Instant.now()
  extension (e: EffectiveInstant)
    def value: Instant = e
    def toLocalDate: LocalDate = e.atZone(ZoneOffset.UTC).toLocalDate
  given CanEqual[EffectiveInstant, EffectiveInstant] = CanEqual.derived
