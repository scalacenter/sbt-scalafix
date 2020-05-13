val V = _root_.scalafix.sbt.BuildInfo

scalaVersion := V.scala212
addCompilerPlugin(scalafixSemanticdb)
scalacOptions ++= Seq("-Yrangepos", "-Ywarn-unused")
scalafixConfig := Some(file(".scalafixxx.conf"))
