package com.github.bieli.openinsuranceengine.core.product

import com.github.bieli.openinsuranceengine.core.id.{CoverageId, ProductId}
import com.github.bieli.openinsuranceengine.core.money.Money

/**
 * Line of Business - P&C taxonomy used by carriers worldwide.
 * Extensible via custom codes for niche products.
 */
enum LineOfBusiness:
  case PersonalAuto
  case CommercialAuto
  case Homeowners
  case CommercialProperty
  case GeneralLiability
  case WorkersCompensation
  case InlandMarine
  case Cyber
  case Specialty(code: String)

object LineOfBusiness:
  given CanEqual[LineOfBusiness, LineOfBusiness] = CanEqual.derived

enum CoverageType:
  case Liability
  case Collision
  case Comprehensive
  case UninsuredMotorist
  case MedicalPayments
  case PropertyDamage
  case BodilyInjury
  case Fire
  case Theft
  case Flood
  case Custom(code: String)

object CoverageType:
  given CanEqual[CoverageType, CoverageType] = CanEqual.derived

/** Product definition - template from which policies are issued. */
final case class ProductDefinition(
    id: ProductId,
    code: String,
    name: String,
    lineOfBusiness: LineOfBusiness,
    availableCoverages: List[CoverageTemplate],
    effectiveFrom: java.time.LocalDate,
    effectiveTo: Option[java.time.LocalDate] = None
)

final case class CoverageTemplate(
    code: String,
    coverageType: CoverageType,
    defaultLimit: Option[Money],
    defaultDeductible: Option[Money],
    isMandatory: Boolean = false
)

/** Coverage instance attached to a policy period. */
final case class Coverage(
    id: CoverageId,
    code: String,
    coverageType: CoverageType,
    limit: Money,
    deductible: Money,
    premium: Money
)
