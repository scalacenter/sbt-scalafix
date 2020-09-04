import _root_.scalafix.sbt.{BuildInfo => Versions}

inThisBuild(
  Seq(
    scalaVersion := Versions.scala212,
    scalacOptions ++= List(
      "-Yrangepos",
      "-Ywarn-unused-import"
    )
  )
)
lazy val lint = project
  .settings(
    addCompilerPlugin(scalafixSemanticdb)
  )

lazy val rewrite = project
  .configs(IntegrationTest)
  .settings(
    Defaults.itSettings,
    addCompilerPlugin(scalafixSemanticdb)
  )
  .settings(scalafixConfigSettings(IntegrationTest): _*)
