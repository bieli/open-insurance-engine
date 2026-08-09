package com.github.bieli.openinsuranceengine.core.party

import com.github.bieli.openinsuranceengine.core.id.PartyId

/** Party roles mirrored after domain Account / Contact model. */
enum PartyRole:
  case PolicyHolder, Insured, Driver, Beneficiary, Claimant, Payor, Broker, Agent, Vendor, Underwriter

object PartyRole:
  given CanEqual[PartyRole, PartyRole] = CanEqual.derived

enum PartyType:
  case Person, Organization

object PartyType:
  given CanEqual[PartyType, PartyType] = CanEqual.derived

final case class PersonName(firstName: String, lastName: String, middleName: Option[String] = None):
  def fullName: String = middleName.fold(s"$firstName $lastName")(m => s"$firstName $m $lastName")

final case class Address(
    street: String,
    city: String,
    postalCode: String,
    country: String,
    region: Option[String] = None
)

sealed trait Party:
  def id: PartyId
  def partyType: PartyType
  def roles: Set[PartyRole]
  def addresses: List[Address]
