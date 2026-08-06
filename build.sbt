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
  .aggregate(policy)
  .settings(
    name := "open-insurance-engine",
    publish / skip := true
  )

lazy val policy = (project in file("modules/policy"))
  .settings(commonSettings)
  .settings(name := "oie-policy")
