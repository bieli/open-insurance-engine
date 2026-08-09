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
