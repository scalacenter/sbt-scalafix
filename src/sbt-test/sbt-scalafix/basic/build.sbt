inThisBuild(
  List(
    scalaVersion := "2.12.6",
    libraryDependencies ++= List(
      "org.scalameta" %% "testkit" % "4.0.0-M6" % Test,
      "org.scalacheck" %% "scalacheck" % "1.13.5" % Test,
      "org.scalatest" %% "scalatest" % "3.0.5" % Test
    )
  )
)

lazy val example = project
  .settings(
    Defaults.itSettings,
    inConfig(IntegrationTest)(scalafixConfigSettings),
    addCompilerPlugin(scalafixSemanticdb),
    scalacOptions += "-Yrangepos"
  )

lazy val tests = project
