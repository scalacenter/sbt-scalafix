import _root_.scalafix.sbt.{BuildInfo => Versions}

inThisBuild(
  Seq(
    scalaVersion := Versions.scala212,
    scalacOptions ++= List(
      "-Yrangepos",
      "-Ywarn-unused"
    )
  )
)

lazy val example = project
  .settings(
    addCompilerPlugin(scalafixSemanticdb)
  )

lazy val noneScalafixConfig = project
  .settings(
    scalafixConfig := None,
    addCompilerPlugin(scalafixSemanticdb)
  )
