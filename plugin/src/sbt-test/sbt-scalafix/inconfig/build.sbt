inThisBuild(
  List(
    scalaVersion := "2.12.20",
    libraryDependencies ++= List(
      "org.scalameta" %% "testkit" % "4.3.10" % Test,
      "org.scalameta" %% "munit" % "0.7.9" % Test,
      "org.scalacheck" %% "scalacheck" % "1.13.5" % Test,
      "org.scalatest" %% "scalatest" % "3.2.19" % Test
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
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision
  )
  .settings(scalafixConfigSettings(IntegrationTest): _*)

lazy val tests = project
