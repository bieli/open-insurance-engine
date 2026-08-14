package com.github.bieli.openinsuranceengine.workflow

import cats.effect.IO
import com.github.bieli.openinsuranceengine.core.result.DomainError
import com.github.bieli.openinsuranceengine.core.result.DomainResult
import com.github.bieli.openinsuranceengine.rules.{Rule, RuleSet}
import munit.CatsEffectSuite

/**
 * New-business submission workflow with an underwriting gate —
 * mirrors PolicyCenter job progression: validate -> underwrite -> bind.
 */
class SubmissionWorkflowSuite extends CatsEffectSuite:

  final case class Submission(premiumMinor: Long, referred: Boolean = false)

  private val uwGate: RuleSet[Submission] = RuleSet(
    id = "RS_SUBMISSION_UW",
    name = "Submission UW gate",
    rules = List(
      Rule.rejectWhen[Submission](
        "NO_PREMIUM",
        "Premium required",
        1,
        _.premiumMinor <= 0,
        _ => "Cannot bind zero-premium submission"
      ),
      Rule.referWhen[Submission](
        "HIGH_PREMIUM",
        "Above authority",
        10,
        _.premiumMinor > 500_000L,
        s => s"Premium ${s.premiumMinor} exceeds binder authority"
      )
    )
  )

  private val definition =
    WorkflowDefinition[Submission, IO](
      id = "nb-submission",
      name = "New Business Submission",
      initialStepId = "validate",
      steps = Map(
        "validate" -> WorkflowStep(
          id = "validate",
          name = "Validate",
          execute = s => IO.pure(if s.premiumMinor >= 0 then Right(s) else DomainResult.raise(DomainError.ValidationFailed("PREMIUM", "bad"))),
          transitions = List(Transition("underwrite"))
        ),
        "underwrite" -> WorkflowStep(
          id = "underwrite",
          name = "Underwrite",
          execute = s =>
            IO.pure:
              uwGate.evaluate(s).map: result =>
                s.copy(referred = result.referrals.nonEmpty),
          transitions = List(
            Transition("bind", guard = !_.referred, description = "clear to bind"),
            Transition("refer", guard = _.referred, description = "UW referral desk")
          )
        ),
        "bind" -> WorkflowStep(
          id = "bind",
          name = "Bind",
          execute = s => IO.pure(Right(s)),
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

  private val engine = WorkflowEngine[IO, Submission]

  private def runToEnd(start: Submission): IO[WorkflowInstance[Submission]] =
    for
      s0 <- engine.start(definition, start).map(_.toOption.get)
      s1 <- engine.advance(s0, definition).map(_.toOption.get) // validate -> underwrite
      s2 <- engine.advance(s1, definition).map(_.toOption.get) // underwrite -> bind|refer
      s3 <- engine.advance(s2, definition).map(_.toOption.get) // terminal
    yield s3

  test("clean submission binds through workflow"):
    for done <- runToEnd(Submission(premiumMinor = 120_000L))
    yield
      assertEquals(done.status, WorkflowStatus.Completed)
      assertEquals(done.history.map(_.stepId), List("validate", "underwrite", "bind"))
      assert(!done.state.referred)

  test("high-premium submission routes to UW referral desk"):
    for done <- runToEnd(Submission(premiumMinor = 800_000L))
    yield
      assertEquals(done.status, WorkflowStatus.Completed)
      assertEquals(done.history.map(_.stepId), List("validate", "underwrite", "refer"))
      assert(done.state.referred)

  test("zero-premium submission fails at underwrite gate"):
    for
      s0 <- engine.start(definition, Submission(0L)).map(_.toOption.get)
      s1 <- engine.advance(s0, definition).map(_.toOption.get)
      s2 <- engine.advance(s1, definition)
    yield assert(s2.isLeft)
