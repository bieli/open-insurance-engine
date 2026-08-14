ThisBuild / organization := "com.github.bieli.openinsuranceengine"
ThisBuild / version := "0.0.1"
ThisBuild / scalaVersion := "3.3.4"

// Target bytecode compatible with JDK 11+ (avoids mixed 8/11/17 classfile issues across IDEs)
ThisBuild / scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-language:strictEquality",
  "-release:11"
)
ThisBuild / javacOptions ++= Seq("--release", "11")

val catsEffectV = "3.5.7"
val fs2V = "3.11.0"
val fs2KafkaV = "3.5.1"
val circeV = "0.14.10"
val declineV = "2.4.1"
val munitV = "1.0.2"
val munitCEV = "2.0.0"
val log4catsV = "2.7.0"
val logbackV = "1.5.12"
val pureconfigV = "0.17.8"

lazy val commonSettings = Seq(
  libraryDependencies ++= Seq(
    "org.typelevel" %% "cats-effect" % catsEffectV,
    "co.fs2" %% "fs2-core" % fs2V,
    "io.circe" %% "circe-core" % circeV,
    "io.circe" %% "circe-generic" % circeV,
    "io.circe" %% "circe-parser" % circeV,
    "org.typelevel" %% "log4cats-slf4j" % log4catsV,
    "org.scalameta" %% "munit" % munitV % Test,
    "org.typelevel" %% "munit-cats-effect" % munitCEV % Test
  )
)

lazy val root = (project in file("."))
  .aggregate(core, rules, validation, policy, rating, plugins, billing, workflow, claim, app)
  .settings(
    name := "open-insurance-engine",
    publish / skip := true
  )

lazy val core = (project in file("modules/core"))
  .settings(commonSettings)
  .settings(name := "oie-core")

lazy val rules = (project in file("modules/rules"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name := "oie-rules",
    libraryDependencies += "io.circe" %% "circe-yaml" % "0.15.2"
  )

lazy val plugins = (project in file("modules/plugins"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(name := "oie-plugins")

lazy val rating = (project in file("modules/rating"))
  .dependsOn(core, plugins, policy, rules)
  .settings(commonSettings)
  .settings(name := "oie-rating")

lazy val policy = (project in file("modules/policy"))
  .dependsOn(core, rules)
  .settings(commonSettings)
  .settings(name := "oie-policy")

lazy val billing = (project in file("modules/billing"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(name := "oie-billing")

lazy val claim = (project in file("modules/claim"))
  .dependsOn(core, rules, validation, workflow, plugins)
  .settings(commonSettings)
  .settings(name := "oie-claim")

lazy val workflow = (project in file("modules/workflow"))
  .dependsOn(core, rules)
  .settings(commonSettings)
  .settings(name := "oie-workflow")

lazy val validation = (project in file("modules/validation"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(name := "oie-validation")

lazy val app = (project in file("modules/app"))
  .dependsOn(core, rules, validation, workflow, plugins, rating, policy, billing, claim)
  .settings(commonSettings)
  .settings(
    name := "open-insurance-engine-app",
    Compile / run / fork := true,
    libraryDependencies ++= Seq(
      "com.monovore" %% "decline" % declineV,
      "com.monovore" %% "decline-effect" % declineV,
      "com.github.pureconfig" %% "pureconfig-core" % pureconfigV,
      "com.github.pureconfig" %% "pureconfig-generic-scala3" % pureconfigV,
      "ch.qos.logback" % "logback-classic" % logbackV
    )
  )
