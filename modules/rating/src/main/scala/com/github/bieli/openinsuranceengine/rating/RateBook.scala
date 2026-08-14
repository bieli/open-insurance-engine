package com.github.bieli.openinsuranceengine.rating

import com.github.bieli.openinsuranceengine.core.money.{CurrencyCode, Money}
import com.github.bieli.openinsuranceengine.core.result.{DomainError, DomainResult}
import com.github.bieli.openinsuranceengine.core.risk.VehicleRisk
import com.github.bieli.openinsuranceengine.rules.{CatalogFactor, CatalogPlan, CatalogTable, RatingSection, RuleCatalog}

/** Compiles the YAML rating section into `RateTable` / `RatePlan` values. */
object RateBook:

  lazy val section: RatingSection = RuleCatalog.document.rating

  lazy val tables: Map[String, RateTable] =
    section.tables.map(t => t.id -> toTable(t)).toMap

  lazy val plans: Map[String, RatePlan] =
    section.plans.map(p => p.id -> toPlan(p)).toMap

  def table(id: String): RateTable =
    tables.getOrElse(id, throw new IllegalStateException(s"Unknown rate table '$id' in $RuleCatalog.ResourceName"))

  def plan(id: String): RatePlan =
    plans.getOrElse(id, throw new IllegalStateException(s"Unknown rate plan '$id' in $RuleCatalog.ResourceName"))

  def defaultBaseRate: Money =
    val currency = CurrencyCode.unsafe(section.currency)
    Money.fromMajor(section.defaultBaseRateMajor, currency)

  private def toTable(table: CatalogTable): RateTable =
    RateTable(
      id = table.id,
      name = table.name,
      bands = table.bands.zipWithIndex.map: (band, i) =>
        RateBand(
          code = band.code.getOrElse(s"${table.id}-$i"),
          label = band.label,
          min = band.min,
          max = band.max,
          factor = band.factor
        )
    )

  private def toPlan(plan: CatalogPlan): RatePlan =
    val mode = plan.mode.trim.toLowerCase match
      case "weightedaverage" | "weighted" => CombinationMode.WeightedAverage
      case "multiplicative" | "product"   => CombinationMode.Multiplicative
      case other =>
        throw new IllegalStateException(s"Unknown combination mode '$other' on plan '${plan.id}'")
    RatePlan(
      id = plan.id,
      name = plan.name,
      mode = mode,
      factors = plan.factors.map(toFactor)
    )

  private def toFactor(factor: CatalogFactor): FactorDefinition =
    val table = this.table(factor.table)
    FactorDefinition(
      code = factor.code,
      name = factor.name,
      weight = factor.weight,
      extract = req => extract(factor, table, req)
    )

  private def extract(
      factor: CatalogFactor,
      table: RateTable,
      req: RatingRequest
  ): DomainResult[(String, RateBand)] =
    factor.source.trim.toLowerCase match
      case "profile.age" =>
        numericLookup(table, BigDecimal(req.profile.age))
      case "profile.yearslicensed" =>
        numericLookup(table, BigDecimal(req.profile.yearsLicensed))
      case "profile.priorclaimslast3years" =>
        numericLookup(table, BigDecimal(req.profile.priorClaimsLast3Years))
      case "profile.creditband" =>
        categoricalLookup(table, req.profile.creditBand.toString, factor.fallback)
      case "profile.regioncode" =>
        categoricalLookup(table, req.profile.regionCode, factor.fallback)
      case "vehicle.annualmileage" =>
        val mileage = req.risk match
          case v: VehicleRisk => v.annualMileage.map(m => BigDecimal(m)).getOrElse(defaultMileage(factor))
          case _              => defaultMileage(factor)
        numericLookup(table, mileage)
      case "vehicle.ageyears" =>
        RatingEngine.Vehicle.ageYears(req).flatMap(years => numericLookup(table, BigDecimal(years)))
      case other =>
        DomainResult.raise(
          DomainError.ValidationFailed(
            "RATE_SOURCE",
            s"Unknown rating source '$other' on factor '${factor.code}'"
          )
        )

  private def defaultMileage(factor: CatalogFactor): BigDecimal =
    factor.defaultValue.getOrElse(BigDecimal(12000))

  private def numericLookup(table: RateTable, value: BigDecimal): DomainResult[(String, RateBand)] =
    table.lookup(value).map(band => (value.bigDecimal.stripTrailingZeros.toPlainString, band))

  private def categoricalLookup(
      table: RateTable,
      key: String,
      fallback: Option[String]
  ): DomainResult[(String, RateBand)] =
    val resolved =
      if table.bands.exists(_.code.equalsIgnoreCase(key)) then key
      else fallback.getOrElse(key)
    table.lookupExact(resolved).map(band => (key, band))
