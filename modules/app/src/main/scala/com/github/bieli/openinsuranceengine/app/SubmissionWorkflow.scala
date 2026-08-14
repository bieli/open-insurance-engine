package com.github.bieli.openinsuranceengine.app

import cats.effect.IO
import com.github.bieli.openinsuranceengine.core.id.EntityId
import com.github.bieli.openinsuranceengine.core.result.{DomainError, DomainResult}
import com.github.bieli.openinsuranceengine.policy.{PolicyRules, PolicyService, PolicyPeriod}
import com.github.bieli.openinsuranceengine.rating.{
  InsuredProfile,
  PersonalAutoRatePlan,
  PolicyRatingPlugin,
  RateWorksheet,
  RatingEngine
}
import com.github.bieli.openinsuranceengine.workflow.{Transition, WorkflowDefinition, WorkflowStep}

/** Payload carried through the new-business submission workflow. */
final case class SubmissionState(
    period: PolicyPeriod,
    insured: InsuredProfile,
    worksheets: List[RateWorksheet] = Nil,
    referred: Boolean = false,
    referralReasons: List[String] = Nil
)

/**
 * PolicyCenter-style job: draft -> rate -> underwrite -> quote|refer -> bind.
 */
object SubmissionWorkflow:

  val id = "nb-submission"
  val name = "New Business Submission"

  def definition(
      policy: PolicyService[IO],
      ratingEngine: RatingEngine
  ): WorkflowDefinition[SubmissionState, IO] =
    WorkflowDefinition(
      id = id,
      name = name,
      initialStepId = "draft",
      steps = Map(
        "draft" -> WorkflowStep(
          id = "draft",
          name = "Create draft",
          execute = s =>
            policy.createDraft(s.period).map:
              case Right(p)  => Right(s.copy(period = p))
              case Left(err) => DomainResult.raise(DomainError.ValidationFailed("DRAFT", err))
          ,
          transitions = List(Transition("rate"))
        ),
        "rate" -> WorkflowStep(
          id = "rate",
          name = "Rate policy",
          execute = s =>
            IO.pure:
              PolicyRatingPlugin
                .rateWithWorksheets(ratingEngine, PersonalAutoRatePlan.weightedPlan, s.insured, s.period)
                .map: (rated, sheets) =>
                  s.copy(period = rated, worksheets = sheets)
          ,
          transitions = List(Transition("underwrite"))
        ),
        "underwrite" -> WorkflowStep(
          id = "underwrite",
          name = "Underwrite",
          execute = s =>
            IO.pure:
              PolicyRules.personalAutoRuleSet
                .evaluate(PolicyRules.UnderwritingContext(s.period, Some(s.insured.age)))
                .map: result =>
                  s.copy(
                    referred = result.referrals.nonEmpty,
                    referralReasons = result.referrals.flatMap(_.messages)
                  )
          ,
          transitions = List(
            Transition("quote", guard = !_.referred, description = "clear to quote"),
            Transition("refer", guard = _.referred, description = "UW referral desk")
          )
        ),
        "quote" -> WorkflowStep(
          id = "quote",
          name = "Quote",
          execute = s =>
            policy.quote(s.period).map:
              case Right(p)  => Right(s.copy(period = p))
              case Left(err) => DomainResult.raise(DomainError.Conflict("QUOTE", err))
          ,
          transitions = List(Transition("bind"))
        ),
        "bind" -> WorkflowStep(
          id = "bind",
          name = "Bind",
          execute = s =>
            val number = s"POL-${s.period.policyId.asString.take(8).toUpperCase}"
            policy.bind(s.period, number).map:
              case Right(p)  => Right(s.copy(period = p))
              case Left(err) => DomainResult.raise(DomainError.Conflict("BIND", err))
          ,
          transitions = Nil,
          isTerminal = true
        ),
        "refer" -> WorkflowStep(
          id = "refer",
          name = "Refer to UW",
          execute = s => IO.pure(Right(s)),
          transitions = Nil,
          isTerminal = true
        )
      )
    )
