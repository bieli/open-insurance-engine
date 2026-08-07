package com.github.bieli.openinsuranceengine.rules

import com.github.bieli.openinsuranceengine.core.result.DomainError
import munit.FunSuite

class RuleEngineSuite extends FunSuite:
  final case class Ctx(value: Int, flag: Boolean = false)

  test("reject rule stops evaluation"):
    val rules = RuleSet(
      "test",
      "Test",
      List(
        Rule.rejectWhen[Ctx]("r1", "too small", 1, _.value < 0, _ => "negative"),
        Rule.modify[Ctx]("r2", "increment", 2, c => c.copy(value = c.value + 1))
      )
    )
    val result = rules.evaluate(Ctx(-1), stopOnReject = true)
    assert(result.isLeft)
    result.left.foreach: errs =>
      assert(errs.head.isInstanceOf[DomainError.RuleViolation])
