import _root_.scalafix.sbt.{BuildInfo => Versions}

inThisBuild(
  List(
    scalafixDependencies := List(
      // Custom rule published to Maven Central https://github.com/olafurpg/example-scalafix-rule
      "com.geirsson" %% "example-scalafix-rule" % "1.3.0"
    )
  )
)

val rules = project
  .disablePlugins(ScalafixPlugin)
  .settings(
    scalaVersion := Versions.scala212,
    crossPaths := false,
    libraryDependencies += "ch.epfl.scala" %% "scalafix-core" % Versions.scalafixVersion,
    libraryDependencies += "joda-time" % "joda-time" % "2.10.6"
  )

val service = project
  .dependsOn(rules % ScalafixConfig)
  .settings(
    scalaVersion := Versions.scala213
  )
