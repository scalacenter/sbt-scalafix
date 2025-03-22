import _root_.scalafix.sbt.{BuildInfo => Versions}
inThisBuild(
  List(
    updateOptions := updateOptions.value.withLatestSnapshots(false),
    scalacOptions += "-Ywarn-adapted-args", // For NoAutoTupling
    resolvers += Resolver.sonatypeRepo("public")
  )
)

lazy val scalafixSettings = List(
  semanticdbEnabled := true,
  semanticdbVersion := scalafixSemanticdb.revision
)

lazy val root = project
  .in(file("."))
  .aggregate(
    javaProject,
    scala212,
    scala213
  )

lazy val scala212 = project
  .configs(IntegrationTest)
  .settings(
    Defaults.itSettings,
    Compile / scalafix / unmanagedSources :=
      (Compile / unmanagedSources).value
        .filterNot(_.getAbsolutePath.contains("IgnoreMe")),
    scalaVersion := Versions.scala212,
    scalafixSettings
  )
  .settings(scalafixConfigSettings(IntegrationTest): _*)

lazy val scala213 = project.settings(
  scalaVersion := Versions.scala213,
  scalacOptions += "-Xsource:3",
  scalafixSettings,
  // NoAutoTupling is not supported
  scalacOptions -= "-Ywarn-adapted-args",
  scalafixConfig := Some(file("213.scalafix.conf"))
)

lazy val javaProject = project.settings(
  scalaVersion := Versions.scala212,
  scalafixSettings
)
