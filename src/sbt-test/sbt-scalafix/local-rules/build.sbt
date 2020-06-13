import _root_.scalafix.sbt.{BuildInfo => Versions}

inThisBuild(
  List(
    scalafixDependencies := List(
      // Custom rule published to Maven Central https://github.com/scalacenter/example-scalafix-rule
      "ch.epfl.scala" %% "example-scalafix-rule" % "1.4.0"
    ),
    // `Repository.central()` can only be used sbt 1.x / scala 2.12
    // error: Static methods in interface require -target:jvm-1.8
    scalafixResolvers := List(
      coursierapi.MavenRepository.of("https://repo1.maven.org/maven2")
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
    scalaVersion := Versions.scala213,
    libraryDependencies += "com.nequissimus" % "sort-imports_2.12" % "0.5.0" % ScalafixConfig
  )

val sameproject = project
  .settings(
    scalaVersion := Versions.scala212, // the project scala version MUST match the one used by Scalafix
    libraryDependencies += "ch.epfl.scala" %% "scalafix-core" % Versions.scalafixVersion % ScalafixConfig
  )
