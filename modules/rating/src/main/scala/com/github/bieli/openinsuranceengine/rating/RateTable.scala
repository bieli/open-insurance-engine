package com.github.bieli.openinsuranceengine.rating

import com.github.bieli.openinsuranceengine.core.result.{DomainError, DomainResult}

/**
 * Lookup table: maps an observed input (age, mileage, ...) onto a rating band + factor.
 * Tables are the core reusable building block of a Guidewire-style rate book.
 */
final case class RateBand(
    code: String,
    label: String,
    /** Inclusive lower bound; None = -∞ */
    min: Option[BigDecimal],
    /** Exclusive upper bound; None = +∞ */
    max: Option[BigDecimal],
    factor: BigDecimal
):
  def contains(value: BigDecimal): Boolean =
    min.forall(value >= _) && max.forall(value < _)

final case class RateTable(
    id: String,
    name: String,
    bands: List[RateBand]
):
  def lookup(value: BigDecimal): DomainResult[RateBand] =
    bands.find(_.contains(value)) match
      case Some(band) => DomainResult.pure(band)
      case None =>
        DomainResult.raise(
          DomainError.ValidationFailed(
            "RATE_BAND",
            s"No band in table '$id' for value $value"
          )
        )

  def lookupExact(key: String): DomainResult[RateBand] =
    bands.find(_.code.equalsIgnoreCase(key)) match
      case Some(band) => DomainResult.pure(band)
      case None =>
        DomainResult.raise(
          DomainError.NotFound("RATE_BAND", s"No band '$key' in table '$id'")
        )

object RateTable:
  /** Convenience builder for contiguous numeric bands. */
  def numeric(
      id: String,
      name: String,
      entries: (Option[BigDecimal], Option[BigDecimal], String, BigDecimal)*
  ): RateTable =
    RateTable(
      id = id,
      name = name,
      bands = entries.toList.zipWithIndex.map: (e, i) =>
        val (min, max, label, factor) = e
        RateBand(code = s"$id-$i", label = label, min = min, max = max, factor = factor)
    )
