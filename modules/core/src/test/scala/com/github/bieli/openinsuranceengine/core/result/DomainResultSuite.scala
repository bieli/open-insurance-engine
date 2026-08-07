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

  test("combine accumulates multiple errors"):
    final case class Form(name: String, age: Int)
    val v = Validator.combine[Form](
      Validator(f => Field.nonBlank("name", f.name).as(f)),
      Validator(f => Field.inRange("age", f.age, 18, 99).as(f))
    )
    val result = v.validate(Form("  ", 10))
    assert(result.isInvalid)
    result match
      case Validated.Invalid(errs) => assertEquals(errs.size, 2)
      case Validated.Valid(_)      => fail("expected invalid")

  test("toDomainResult maps ValidationFailed to DomainError"):
    import Validator.toDomainResult
    val invalid = Field.required("f", Option.empty[String]).toDomainResult
    assert(invalid.isLeft)
    invalid.left.foreach: nel =>
      assert(nel.head.isInstanceOf[DomainError.ValidationFailed])

  test("pure validator always accepts"):
    assertEquals(Validator.pure[Int].validate(7), Validated.validNel(7))
