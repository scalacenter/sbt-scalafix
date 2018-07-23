lazy val scalafixSettings = List(
  addCompilerPlugin(scalafixSemanticdb),
  scalacOptions += "-Yrangepos"
)

ThisBuild / scalaVersion := "2.12.6"

lazy val root = (project in file("."))
  .settings(
    scalafixSettings
  )
