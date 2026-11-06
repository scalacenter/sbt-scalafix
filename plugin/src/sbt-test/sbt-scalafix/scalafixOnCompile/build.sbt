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

lazy val Custom = config("custom").extend(Runtime)
lazy val rewrite = project
  .configs(Custom)
  .settings(
    inConfig(Custom)(Defaults.configSettings)
  )
  .settings(scalafixConfigSettings(Custom): _*)

lazy val triggered = project
