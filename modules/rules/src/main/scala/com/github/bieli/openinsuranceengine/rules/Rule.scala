package com.github.bieli.openinsuranceengine.rules

import cats.data.NonEmptyList
import com.github.bieli.openinsuranceengine.core.result.{DomainError, DomainResult}

/**
 * Generic business-rules engine inspired by domain rules / Rule Sets.
 *
 * Rules are pure functions over a context `C`. They can be composed into
 * RuleSets, evaluated sequentially or as a pipeline of priority-ordered rules.
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

final case class RuleSet[C](
    id: String,
    name: String,
    rules: List[Rule[C]]
):
  def sorted: RuleSet[C] =
    copy(rules = rules.filter(_.enabled).sortBy(_.priority))

  /** Evaluate all rules; collect violations; stop on Reject if stopOnReject. */
  def evaluate(ctx: C, stopOnReject: Boolean = true): DomainResult[RuleSetResult[C]] =
    sorted.rules
      .foldLeft[DomainResult[RuleSetResult[C]]](Right(RuleSetResult(ctx, Nil, Nil))): (acc, rule) =>
        acc.flatMap: result =>
          if stopOnReject && result.rejected then Right(result)
          else
            rule.evaluate(result.context).map: outcome =>
              val fired = FiredRule(rule.id, rule.name, outcome.action, outcome.messages)
              outcome.action match
                case RuleAction.Reject(reason) =>
                  result.copy(
                    context = outcome.context,
                    fired = result.fired :+ fired,
                    violations = result.violations :+ DomainError.RuleViolation(
                      code = s"RULE_${rule.id}",
                      message = reason,
                      ruleId = rule.id
                    )
                  )
                case _ =>
                  result.copy(context = outcome.context, fired = result.fired :+ fired)
      .flatMap: result =>
        NonEmptyList.fromList(result.violations) match
          case Some(errs) if stopOnReject => Left(errs)
          case _                          => Right(result)

final case class FiredRule(
    ruleId: String,
    ruleName: String,
    action: RuleAction,
    messages: List[String]
)

final case class RuleSetResult[C](
    context: C,
    fired: List[FiredRule],
    violations: List[DomainError.RuleViolation]
):
  def rejected: Boolean = violations.nonEmpty
  def referrals: List[FiredRule] =
    fired.filter:
      case FiredRule(_, _, RuleAction.Refer(_), _) => true
      case _                                       => false

object Rule:
  /** Lift a predicate into a reject/accept rule. */
  def rejectWhen[C](
      id: String,
      name: String,
      priority: Int,
      predicate: C => Boolean,
      reason: C => String,
      enabled: Boolean = true
  ): Rule[C] =
    val (i, n, p, e) = (id, name, priority, enabled)
    new Rule[C]:
      val id: String = i
      val name: String = n
      val priority: Int = p
      val enabled: Boolean = e
      def evaluate(ctx: C): DomainResult[RuleOutcome[C]] =
        if predicate(ctx) then Right(RuleOutcome(RuleAction.Reject(reason(ctx)), ctx))
        else Right(RuleOutcome(RuleAction.Accept, ctx))

  def modify[C](
      id: String,
      name: String,
      priority: Int,
      transform: C => C,
      enabled: Boolean = true
  ): Rule[C] =
    val (i, n, p, e) = (id, name, priority, enabled)
    new Rule[C]:
      val id: String = i
      val name: String = n
      val priority: Int = p
      val enabled: Boolean = e
      def evaluate(ctx: C): DomainResult[RuleOutcome[C]] =
        Right(RuleOutcome(RuleAction.Modify, transform(ctx)))

  def referWhen[C](
      id: String,
      name: String,
      priority: Int,
      predicate: C => Boolean,
      reason: C => String,
      enabled: Boolean = true
  ): Rule[C] =
    val (i, n, p, e) = (id, name, priority, enabled)
    new Rule[C]:
      val id: String = i
      val name: String = n
      val priority: Int = p
      val enabled: Boolean = e
      def evaluate(ctx: C): DomainResult[RuleOutcome[C]] =
        if predicate(ctx) then Right(RuleOutcome(RuleAction.Refer(reason(ctx)), ctx, List(reason(ctx))))
        else Right(RuleOutcome(RuleAction.Accept, ctx))
