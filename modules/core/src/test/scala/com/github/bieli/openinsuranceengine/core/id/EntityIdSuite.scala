package com.github.bieli.openinsuranceengine.core.id

import java.util.UUID
import munit.FunSuite

class EntityIdSuite extends FunSuite:
  test("random produces unique ids"):
    val a = EntityId.random[PolicyTag]()
    val b = EntityId.random[PolicyTag]()
    assert(a.asString != b.asString)

  test("fromString round-trip"):
    val uuid = UUID.randomUUID()
    val id = EntityId.fromString[ClaimTag](uuid.toString)
    assertEquals(id.map(_.asString), Right(uuid.toString))
    assertEquals(id.map(_.value), Right(uuid))

  test("fromString rejects invalid uuid"):
    assertEquals(EntityId.fromString[AccountTag]("not-a-uuid"), Left("Invalid EntityId: not-a-uuid"))
    assert(EntityId.fromString[AccountTag]("").isLeft)
    assert(EntityId.fromString[AccountTag]("1234").isLeft)

  test("apply wraps uuid"):
    val uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
    val id: PolicyId = EntityId[PolicyTag](uuid)
    assertEquals(id.value, uuid)

  test("Eq compares by uuid value"):
    val uuid = UUID.randomUUID()
    val a = EntityId[PartyTag](uuid)
    val b = EntityId[PartyTag](uuid)
    assert(summon[cats.Eq[PartyId]].eqv(a, b))
