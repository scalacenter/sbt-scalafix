import _root_.scalafix.sbt.{BuildInfo => Versions}

val rules = project
  .disablePlugins(ScalafixPlugin)
  .settings(
    scalaVersion := Versions.scala212,
    libraryDependencies += "ch.epfl.scala" %% "scalafix-core" % Versions.scalafixVersion,
    libraryDependencies += "joda-time" % "joda-time" % "2.10.6"
  )

val service = project
  .dependsOn(rules % ScalafixConfig)
  .settings(
    scalaVersion := Versions.scala212
  )
