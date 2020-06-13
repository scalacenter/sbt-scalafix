inThisBuild(
  List(
    scalaVersion := "2.12.11",
    libraryDependencies ++= List(
      "org.scalameta" %% "testkit" % "4.0.0-M6" % Test,
      "org.scalacheck" %% "scalacheck" % "1.13.5" % Test,
      "org.scalatest" %% "scalatest" % "3.1.2" % Test
    ),
    scalafixDependencies := List(
      // Custom rule published to Maven Central https://github.com/scalacenter/example-scalafix-rule
      "ch.epfl.scala" %% "example-scalafix-rule" % "1.4.0"
    )
  )
)

lazy val example = project
  .settings(
    Defaults.itSettings,
    inConfig(IntegrationTest)(scalafixConfigSettings(IntegrationTest)),
    addCompilerPlugin(scalafixSemanticdb),
    scalacOptions += "-Yrangepos"
  )

lazy val tests = project
