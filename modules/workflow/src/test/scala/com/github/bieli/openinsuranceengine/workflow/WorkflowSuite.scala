package com.github.bieli.openinsuranceengine.workflow

import cats.effect.IO
import com.github.bieli.openinsuranceengine.core.result.DomainError
import munit.CatsEffectSuite

class WorkflowSuite extends CatsEffectSuite:
  private val engine = WorkflowEngine[IO, Int]

  private def linear =
    WorkflowDefinition[Int, IO](
      id = "linear",
      name = "Linear",
      initialStepId = "a",
      steps = Map(
        "a" -> WorkflowStep("a", "A", s => IO.pure(Right(s + 1)), List(Transition("b"))),
        "b" -> WorkflowStep("b", "B", s => IO.pure(Right(s + 1)), Nil, isTerminal = true)
      )
    )

  test("linear workflow completes"):
    for
      started <- engine.start(linear, 0)
      s1 <- engine.advance(started.toOption.get, linear)
      s2 <- engine.advance(s1.toOption.get, linear)
    yield
      assertEquals(s2.map(_.status), Right(WorkflowStatus.Completed))
      assertEquals(s2.map(_.state), Right(2))

  test("start fails when initial step missing"):
    val bad = linear.copy(initialStepId = "missing")
    for result <- engine.start(bad, 0)
    yield
      assert(result.isLeft)
      result.left.foreach: errs =>
        assert(errs.head.isInstanceOf[DomainError.WorkflowError])

  test("advance fails when no transition matches"):
    val stuck = WorkflowDefinition[Int, IO](
      id = "stuck",
      name = "Stuck",
      initialStepId = "a",
      steps = Map(
        "a" -> WorkflowStep(
          "a",
          "A",
          s => IO.pure(Right(s)),
          List(Transition("b", guard = _ => false)),
          isTerminal = false
        ),
        "b" -> WorkflowStep("b", "B", s => IO.pure(Right(s)), Nil, isTerminal = true)
      )
    )
    for
      started <- engine.start(stuck, 0)
      advanced <- engine.advance(started.toOption.get, stuck)
    yield
      assert(advanced.isLeft)
      advanced.left.foreach: errs =>
        assertEquals(errs.head.code, "WF_NO_TRANSITION")

  test("advance fails for unknown current step"):
    for
      started <- engine.start(linear, 0)
      broken = started.toOption.get.copy(currentStepId = Some("ghost"))
      advanced <- engine.advance(broken, linear)
    yield assert(advanced.isLeft)

  test("advance fails when currentStepId is empty"):
    for
      started <- engine.start(linear, 0)
      broken = started.toOption.get.copy(currentStepId = None)
      advanced <- engine.advance(broken, linear)
    yield
      assert(advanced.isLeft)
      advanced.left.foreach: errs =>
        assertEquals(errs.head.code, "WF_NO_STEP")

  test("cancel marks workflow Cancelled"):
    for
      started <- engine.start(linear, 0)
      cancelled <- engine.cancel(started.toOption.get, "user abort")
    yield
      assertEquals(cancelled.status, WorkflowStatus.Cancelled)
      assert(cancelled.history.last.error.contains("user abort"))

  test("step execution failure propagates"):
    val failing = WorkflowDefinition[Int, IO](
      id = "fail",
      name = "Fail",
      initialStepId = "a",
      steps = Map(
        "a" -> WorkflowStep(
          "a",
          "A",
          _ => IO.pure(Left(cats.data.NonEmptyList.one(DomainError.Unexpected("X", "boom")))),
          List(Transition("b"))
        )
      )
    )
    for
      started <- engine.start(failing, 0)
      advanced <- engine.advance(started.toOption.get, failing)
    yield assert(advanced.isLeft)

  test("guard selects correct branch"):
    val branched = WorkflowDefinition[Int, IO](
      id = "branch",
      name = "Branch",
      initialStepId = "start",
      steps = Map(
        "start" -> WorkflowStep(
          "start",
          "Start",
          s => IO.pure(Right(s)),
          List(
            Transition("low", guard = _ < 10),
            Transition("high", guard = _ >= 10)
          )
        ),
        "low" -> WorkflowStep("low", "Low", s => IO.pure(Right(s)), Nil, isTerminal = true),
        "high" -> WorkflowStep("high", "High", s => IO.pure(Right(s)), Nil, isTerminal = true)
      )
    )
    for
      s0 <- engine.start(branched, 15)
      s1 <- engine.advance(s0.toOption.get, branched)
      s2 <- engine.advance(s1.toOption.get, branched)
    yield
      assertEquals(s1.map(_.currentStepId), Right(Some("high")))
      assertEquals(s2.map(_.status), Right(WorkflowStatus.Completed))
