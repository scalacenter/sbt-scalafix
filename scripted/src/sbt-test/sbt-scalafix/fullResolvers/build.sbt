import _root_.scalafix.sbt.{BuildInfo => Versions}

val ruleVersion = "0.0.1"

lazy val ruleRepository =
  settingKey[sbt.librarymanagement.MavenRepository]("")

def ruleSettings(repositoryName: String) = Seq(
  libraryDependencies += "ch.epfl.scala" %% "scalafix-core" % Versions.scalafixVersion,
  organization := "test.scalafix",
  name := "test-rule",
  version := ruleVersion,
  target := baseDirectory.value / "target",
  ruleRepository := sbt.librarymanagement.MavenRepository(
    repositoryName,
    (target.value / repositoryName).toURI.toString
  ),
  publishTo := Some(ruleRepository.value)
)

lazy val rewrite = project
  .disablePlugins(ScalafixPlugin)
  .settings(ruleSettings("rewrite"))

lazy val lint = project
  .disablePlugins(ScalafixPlugin)
  .settings(ruleSettings("lint"))

lazy val example = project

inThisBuild(
  List(
    scalaVersion := Versions.scala213,
    scalafixDependencies += "test.scalafix" %% "test-rule" % ruleVersion,
    scalafixResolvers += coursierapi.MavenRepository.of(
      (lint / ruleRepository).value.root
    )
  )
)

fullResolvers ++= (rewrite / publishTo).value.toSeq
