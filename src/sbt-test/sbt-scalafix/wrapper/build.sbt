val V = _root_.scalafix.sbt.BuildInfo

scalaVersion := V.scala212
semanticdbEnabled := true
semanticdbVersion := scalafixSemanticdb.revision
scalacOptions += "-Ywarn-unused"
