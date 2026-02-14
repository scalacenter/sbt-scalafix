val V = _root_.scalafix.sbt.BuildInfo

scalaVersion := V.scala212
semanticdbEnabled := true
semanticdbVersion := scalafixSemanticdb.revision
scalacOptions += "-Ywarn-unused"

Compile / scalafixConfig := Some(file(".compile.scalafix.conf"))
Test / scalafixConfig := Some(file(".test.scalafix.conf"))
