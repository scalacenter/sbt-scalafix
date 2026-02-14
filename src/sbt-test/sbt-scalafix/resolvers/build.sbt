import _root_.scalafix.sbt.{BuildInfo => Versions}

val ruleVersion = "0.0.1-SNAPSHOT"

inThisBuild(
  List(
    scalaVersion := Versions.scala213,
    scalafixDependencies += "test.scalafix" %% "test-rule" % ruleVersion
  )
)

lazy val rules = project
  .disablePlugins(ScalafixPlugin)
  .settings(
    libraryDependencies += "ch.epfl.scala" %% "scalafix-core" % Versions.scalafixVersion,
    organization := "test.scalafix",
    name := "test-rule",
    version := ruleVersion,
    publishTo := Some(
      Resolver.file("custom-ivy", target.value / "custom-ivy")(
        Resolver.ivyStylePatterns
      )
    ),
    publishMavenStyle := false
  )

lazy val example = project
