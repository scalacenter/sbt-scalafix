// support sbt 1.3.x, see https://github.com/sbt/sbt/issues/5110
Global / semanticdbVersion := scalafixSemanticdb.revision
ThisBuild / semanticdbEnabled := true

lazy val scala212 = project
  .settings(
    scalaVersion := "2.12.20",
    scalacOptions += "-Ywarn-unused"
  )

lazy val scala213 = project
  .settings(
    scalaVersion := "2.13.15",
    scalacOptions += "-Wunused"
  )
