package com.github.bieli.openinsuranceengine.core.account

import com.github.bieli.openinsuranceengine.core.id.{AccountTag, EntityId, PartyTag}
import com.github.bieli.openinsuranceengine.core.time.EffectiveInstant
import munit.FunSuite

/**
 * Account model - PolicyCenter-style account / PNI container behaviours.
 */
class AccountSuite extends FunSuite:

  private def newAccount(
      number: String = "ACC-2026-0001",
      status: AccountStatus = AccountStatus.Prospect,
      producer: Option[String] = Some("PRD-WAW-01"),
      org: Option[String] = None
  ): Account =
    Account(
      id = EntityId.random[AccountTag](),
      accountNumber = number,
      primaryNamedInsuredId = EntityId.random[PartyTag](),
      status = status,
      createdAt = EffectiveInstant.now(),
      producerCode = producer,
      organizationName = org
    )

  test("AccountStatus cases are distinct"):
    assert(AccountStatus.Prospect != AccountStatus.Active)
    assert(AccountStatus.Active != AccountStatus.Inactive)
    assert(AccountStatus.Inactive != AccountStatus.Withdrawn)
    assertEquals(AccountStatus.Active, AccountStatus.Active)

  test("prospect personal account can be activated after submission"):
    val prospect = newAccount(status = AccountStatus.Prospect)
    val active = prospect.copy(status = AccountStatus.Active)
    assertEquals(prospect.status, AccountStatus.Prospect)
    assertEquals(active.status, AccountStatus.Active)
    assertEquals(active.accountNumber, prospect.accountNumber)
    assertEquals(active.primaryNamedInsuredId, prospect.primaryNamedInsuredId)

  test("commercial account carries organization name and producer code"):
    val commercial = newAccount(
      number = "ACC-COM-7788",
      status = AccountStatus.Active,
      producer = Some("PRD-KRK-42"),
      org = Some("ACME Logistics Sp. z o.o.")
    )
    assertEquals(commercial.organizationName, Some("ACME Logistics Sp. z o.o."))
    assertEquals(commercial.producerCode, Some("PRD-KRK-42"))
    assert(commercial.organizationName.exists(_.contains("ACME")))

  test("personal account may omit organization name"):
    val personal = newAccount(org = None, producer = Some("PRD-GDA-07"))
    assertEquals(personal.organizationName, None)
    assert(personal.producerCode.isDefined)

  test("direct / unsolicited account may have no producer"):
    val direct = newAccount(producer = None, status = AccountStatus.Prospect)
    assertEquals(direct.producerCode, None)

  test("inactive account retains PNI and account number for audit"):
    val active = newAccount(number = "ACC-LEGACY-99", status = AccountStatus.Active)
    val inactive = active.copy(status = AccountStatus.Inactive)
    assertEquals(inactive.accountNumber, "ACC-LEGACY-99")
    assertEquals(inactive.primaryNamedInsuredId, active.primaryNamedInsuredId)
    assertEquals(inactive.status, AccountStatus.Inactive)

  test("withdrawn prospect is terminal pre-bind status"):
    val withdrawn = newAccount(status = AccountStatus.Withdrawn)
    assertEquals(withdrawn.status, AccountStatus.Withdrawn)
    // still addressable by id / number for history
    assert(withdrawn.accountNumber.nonEmpty)
    assert(withdrawn.id.asString.nonEmpty)

  test("each account gets a unique id"):
    val a = newAccount()
    val b = newAccount()
    assert(a.id.asString != b.id.asString)
