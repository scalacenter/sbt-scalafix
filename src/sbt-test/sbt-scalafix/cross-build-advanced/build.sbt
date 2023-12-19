import _root_.scalafix.sbt.{BuildInfo => Versions}

val scala3Version = "3.3.0"
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

lazy val root = project
  .in(file("."))
  .settings(
    scalaVersion := Versions.scala212,
    crossScalaVersions := Seq(Versions.scala212, Versions.scala213, scala3Version),
    scalacOptions ++= (if (scalaVersion.value.startsWith("2")) Seq("-Ywarn-unused") else Seq()),
    scalafixScalaBinaryVersion := {
      if (scalaBinaryVersion.value == "3") scalafixScalaBinaryVersion.value
      else scalaBinaryVersion.value
    },
    scalafixConfig := {
      if (scalaBinaryVersion.value == "3")
         Some(file(".scalafix-3.conf"))
      else
         Some(file(".scalafix-2.conf"))
    }
  )
