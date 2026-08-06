package com.github.bieli.openinsuranceengine.rules

import cats.data.NonEmptyList
import com.github.bieli.openinsuranceengine.core.result.{DomainError, DomainResult}

/**
 * Generic business-rules engine inspired by domain rules / Rule Sets.
 */
trait Rule[C]:
  def id: String
  def name: String
  def priority: Int
  def enabled: Boolean
  def evaluate(ctx: C): DomainResult[RuleOutcome[C]]

enum RuleAction:
  case Accept
  case Reject(reason: String)
  case Modify
  case Refer(reason: String) // underwriting referral
  case Warn(message: String)

object RuleAction:
  given CanEqual[RuleAction, RuleAction] = CanEqual.derived

final case class RuleOutcome[C](
    action: RuleAction,
    context: C,
    messages: List[String] = Nil
)
