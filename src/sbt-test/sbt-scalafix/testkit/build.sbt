import _root_.scalafix.sbt.{BuildInfo => Versions}

inThisBuild(
  List(
    scalaVersion := Versions.scala212,
    addCompilerPlugin(scalafixSemanticdb),
    scalacOptions += "-Yrangepos"
  )
)

val rules = project
  .disablePlugins(ScalafixPlugin)
  .settings(
    libraryDependencies += "ch.epfl.scala" %% "scalafix-core" % Versions.scalafixVersion
  )

lazy val input = project

lazy val output = project

lazy val tests = project
  .settings(
    libraryDependencies +=
      "ch.epfl.scala" % "scalafix-testkit" % Versions.scalafixVersion % Test cross CrossVersion.full,
    scalafixTestkitOutputSourceDirectories :=
      sourceDirectories.in(output, Compile).value,
    scalafixTestkitInputSourceDirectories :=
      sourceDirectories.in(input, Compile).value,
    scalafixTestkitInputClasspath :=
      fullClasspath.in(input, Compile).value,
    mainClass in (Test, run) := Some("fix.IntputOutputSuite")
  )
  .dependsOn(input, rules)
  .enablePlugins(ScalafixTestkitPlugin)
