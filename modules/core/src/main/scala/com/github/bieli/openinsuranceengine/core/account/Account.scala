package com.github.bieli.openinsuranceengine.core.account

import com.github.bieli.openinsuranceengine.core.id.{AccountId, PartyId}
import com.github.bieli.openinsuranceengine.core.time.EffectiveInstant

/** Account - typical domain style container for policies belonging to a party. */
enum AccountStatus:
  case Prospect, Active, Inactive, Withdrawn

object AccountStatus:
  given CanEqual[AccountStatus, AccountStatus] = CanEqual.derived

final case class Account(
    id: AccountId,
    accountNumber: String,
    primaryNamedInsuredId: PartyId,
    status: AccountStatus,
    createdAt: EffectiveInstant,
    producerCode: Option[String] = None,
    organizationName: Option[String] = None
)
