import _root_.scalafix.sbt.{BuildInfo => Versions}

// support sbt 1.3.x, see https://github.com/sbt/sbt/issues/5110
Global / semanticdbVersion := scalafixSemanticdb.revision

inThisBuild(
  List(
    scalaVersion := Versions.scala212,  
    scalacOptions += "-Ywarn-unused",
    semanticdbEnabled := true
  )
)

val ok_and_ko = project
val ok_only = project
