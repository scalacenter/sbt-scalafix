import _root_.scalafix.sbt.{BuildInfo => Versions}

ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

inThisBuild(
  List(
    scalaVersion := Versions.scala212,
    scalacOptions += "-Ywarn-unused",
    semanticdbEnabled := true
  )
)

val ok_and_ko = project
val ok_only = project
