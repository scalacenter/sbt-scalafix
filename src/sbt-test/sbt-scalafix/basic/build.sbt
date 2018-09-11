inThisBuild(
  List(
    scalaVersion := "2.12.6",
    libraryDependencies ++= List(
      "org.scalameta" %% "testkit" % "4.0.0-M6" % Test,
      "org.scalacheck" %% "scalacheck" % "1.13.5" % Test,
      "org.scalatest" %% "scalatest" % "3.0.5" % Test
    ),
    scalafixDependencies := List(
      // Custom rule published to Maven Central https://github.com/olafurpg/example-scalafix-rule
      "com.geirsson" %% "example-scalafix-rule" % "1.3.0"
    )
  )
)

lazy val example = project
  .settings(
    Defaults.itSettings,
    inConfig(IntegrationTest)(scalafixConfigSettings(IntegrationTest)),
    addCompilerPlugin(scalafixSemanticdb),
    scalacOptions ++= List(
      "-Yrangepos",
      "-Ywarn-unused-import"
    )
  )

lazy val tests = project
