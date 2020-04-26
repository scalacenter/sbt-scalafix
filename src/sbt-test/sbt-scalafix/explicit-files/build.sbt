import _root_.scalafix.sbt.{BuildInfo => Versions}

val explicit = project.settings(
  scalaVersion := Versions.scala212
)
