ThisBuild / semanticdbVersion := scalafixSemanticdb.revision
ThisBuild / semanticdbEnabled := true

lazy val scala212 = project
  .settings(
    scalaVersion := "2.13.17",
    scalacOptions += "-Ywarn-unused"
  )

lazy val scala213 = project
  .settings(
    scalaVersion := "2.13.17",
    scalacOptions += "-Wunused"
  )
