package com.github.bieli.openinsuranceengine.core.result

import cats.data.NonEmptyList
import munit.FunSuite

class DomainResultSuite extends FunSuite:
  test("pure and raise"):
    assertEquals(DomainResult.pure(42), Right(42))
    val err = DomainError.NotFound("X", "missing")
    assertEquals(DomainResult.raise[Int](err), Left(NonEmptyList.one(err)))

  test("raiseAll preserves order"):
    val errs = NonEmptyList.of(
      DomainError.ValidationFailed("A", "a"),
      DomainError.ValidationFailed("B", "b")
    )
    assertEquals(DomainResult.raiseAll[Unit](errs), Left(errs))

  test("fromOption"):
    assertEquals(DomainResult.fromOption(Some("ok"), DomainError.NotFound("N", "n")), Right("ok"))
    assert(DomainResult.fromOption(None, DomainError.NotFound("N", "gone")).isLeft)

  test("error codes are distinct"):
    val errors: List[DomainError] = List(
      DomainError.ValidationFailed("V", "v"),
      DomainError.RuleViolation("R", "r", "rule-1"),
      DomainError.NotFound("N", "n"),
      DomainError.Conflict("C", "c"),
      DomainError.WorkflowError("W", "w", Some("step")),
      DomainError.PluginError("P", "p", "plugin-1"),
      DomainError.IntegrationError("I", "i", "kafka"),
      DomainError.Unexpected("U", "u")
    )
    assertEquals(errors.map(_.code).toSet.size, errors.size)
