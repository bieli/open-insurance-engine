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
