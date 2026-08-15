package com.github.bieli.openinsuranceengine.app.bdd

import io.cucumber.core.cli.Main
import munit.FunSuite

/** Runs Gherkin features under `classpath:features` through Cucumber. */
class BddFeaturesSuite extends FunSuite:
  test("practical insurance process features"):
    val code = Main.run(
      Array(
        "--glue",
        "com.github.bieli.openinsuranceengine.app.bdd",
        "--plugin",
        "pretty",
        "--plugin",
        "html:target/cucumber/html",
        "--plugin",
        "json:target/cucumber/report.json",
        "--monochrome",
        "classpath:features"
      ),
      Thread.currentThread().getContextClassLoader match
        case null => classOf[BddFeaturesSuite].getClassLoader
        case cl   => cl
    )
    assertEquals(code.toInt, 0, "Cucumber scenarios failed — see pretty output above")
