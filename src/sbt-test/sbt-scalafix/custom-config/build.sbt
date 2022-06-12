val V = _root_.scalafix.sbt.BuildInfo

scalaVersion := V.scala212
addCompilerPlugin(scalafixSemanticdb)
scalacOptions ++= Seq("-Yrangepos", "-Ywarn-unused")

Compile / scalafixConfig := Some(file(".compile.scalafix.conf"))
Test / scalafixConfig := Some(file(".test.scalafix.conf"))
