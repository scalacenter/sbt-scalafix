import _root_.scalafix.sbt.{BuildInfo => V}

inThisBuild(
  List(
    addCompilerPlugin(scalafixSemanticdb),
    scalacOptions += "-Yrangepos",
    scalaVersion := V.scala213,
    scalafixScalaBinaryVersion := scalaBinaryVersion.value // this should be the default in sbt-scalafix 1.0
  )
)
