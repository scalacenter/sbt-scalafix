import _root_.scalafix.sbt.{BuildInfo => Versions}

val scala3Version = "3.4.1"
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

lazy val root = project
  .in(file("."))
  .settings(
    scalaVersion := Versions.scala212,
    crossScalaVersions := Seq(
      Versions.scala212,
      Versions.scala213,
      scala3Version
    ),
    scalacOptions += {
      if (scalaVersion.value.startsWith("2.12"))
        "-Ywarn-unused-import"
      else
        "-Wunused:imports"
    }
  )
