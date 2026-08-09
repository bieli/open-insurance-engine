package com.github.bieli.openinsuranceengine.core.party

import com.github.bieli.openinsuranceengine.core.id.EntityId
import munit.FunSuite

class PartySuite extends FunSuite:
  test("PersonName fullName with and without middle"):
    assertEquals(PersonName("Jan", "Kowalski").fullName, "Jan Kowalski")
    assertEquals(PersonName("Jan", "Kowalski", Some("Adam")).fullName, "Jan Adam Kowalski")

  test("Person and Organization party types"):
    val person = Person(
      id = EntityId.random(),
      name = PersonName("Anna", "Nowak"),
      roles = Set(PartyRole.PolicyHolder, PartyRole.Driver),
      addresses = List(Address("ul. Testowa 1", "Kraków", "30-001", "PL"))
    )
    val org = Organization(
      id = EntityId.random(),
      legalName = "PLALHG Insure Sp. z o.o.",
      roles = Set(PartyRole.Broker),
      addresses = Nil,
      taxId = Some("5250000000")
    )
    assertEquals(person.partyType, PartyType.Person)
    assertEquals(org.partyType, PartyType.Organization)
    assert(person.roles.contains(PartyRole.Driver))
