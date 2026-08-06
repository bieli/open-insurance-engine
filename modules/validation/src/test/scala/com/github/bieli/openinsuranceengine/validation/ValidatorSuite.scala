package com.github.bieli.openinsuranceengine.validation

import cats.data.Validated
import cats.syntax.all.*
import com.github.bieli.openinsuranceengine.core.result.DomainError
import munit.FunSuite

class ValidatorSuite extends FunSuite:
  test("Field.required Some/None"):
    assertEquals(Field.required("x", Some(1)), Validated.validNel(1))
    assert(Field.required("x", Option.empty[Int]).isInvalid)

  test("Field.nonBlank rejects whitespace"):
    assert(Field.nonBlank("name", "  ").isInvalid)
    assert(Field.nonBlank("name", "").isInvalid)
    assert(Field.nonBlank("name", "Ada").isValid)

  test("Field.positive edge cases"):
    assert(Field.positive("amt", 1L).isValid)
    assert(Field.positive("amt", 0L).isInvalid)
    assert(Field.positive("amt", -5L).isInvalid)

  test("Field.inRange inclusive bounds"):
    assert(Field.inRange("age", 18, 18, 65).isValid)
    assert(Field.inRange("age", 65, 18, 65).isValid)
    assert(Field.inRange("age", 17, 18, 65).isInvalid)
    assert(Field.inRange("age", 66, 18, 65).isInvalid)

  test("Field.matches pattern"):
    assert(Field.matches("vin", "WVWZZZ1JZYW386752", "[A-HJ-NPR-Z0-9]{17}").isValid)
    assert(Field.matches("vin", "SHORT", "[A-HJ-NPR-Z0-9]{17}").isInvalid)

