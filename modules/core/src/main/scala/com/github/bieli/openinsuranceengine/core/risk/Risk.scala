package com.github.bieli.openinsuranceengine.core.risk

import com.github.bieli.openinsuranceengine.core.money.Money

/**
 * Risk units - generic abstraction over auto vehicles, buildings, etc.
 * Carriers attach rating factors as opaque key/value attributes.
 */
sealed trait RiskUnit:
  def riskType: String
  def description: String
  def attributes: Map[String, String]

final case class VehicleRisk(
    vin: String,
    make: String,
    model: String,
    year: Int,
    licensePlate: Option[String] = None,
    annualMileage: Option[Int] = None,
    attributes: Map[String, String] = Map.empty
) extends RiskUnit:
  val riskType: String = "vehicle"
  def description: String = s"$year $make $model ($vin)"

final case class PropertyRisk(
    addressLine: String,
    city: String,
    postalCode: String,
    constructionType: Option[String] = None,
    yearBuilt: Option[Int] = None,
    replacementCost: Option[Money] = None,
    attributes: Map[String, String] = Map.empty
) extends RiskUnit:
  val riskType: String = "property"
  def description: String = s"$addressLine, $city $postalCode"

final case class GenericRisk(
    riskType: String,
    description: String,
    attributes: Map[String, String] = Map.empty
) extends RiskUnit
