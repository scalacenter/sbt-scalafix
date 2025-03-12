import _root_.scalafix.sbt.{BuildInfo => Versions}

inThisBuild(
  Seq(
    scalaVersion := Versions.scala212,
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    scalacOptions += "-Ywarn-unused"
  )
)

lazy val lint = project

lazy val rewrite = project
  .configs(IntegrationTest)
  .settings(
    Defaults.itSettings
  )
  .settings(scalafixConfigSettings(IntegrationTest): _*)

lazy val triggered = project
