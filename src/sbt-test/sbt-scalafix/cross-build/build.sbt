import _root_.scalafix.sbt.{BuildInfo => Versions}
inThisBuild(
  List(
    updateOptions := updateOptions.value.withLatestSnapshots(false),
    scalacOptions += "-Ywarn-adapted-args", // For NoAutoTupling
    resolvers += Resolver.sonatypeRepo("public")
  )
)

lazy val scalafixSettings = List(
  addCompilerPlugin(scalafixSemanticdb),
  scalacOptions += "-Yrangepos"
)

lazy val root = project
  .in(file("."))
  .aggregate(
    javaProject,
    scala211,
    scala210,
    scala212
  )

lazy val scala210 = project.settings(
  scalaVersion := "2.10.7"
)
lazy val scala211 = project.settings(
  scalaVersion := Versions.scala211,
  scalafixSettings
)
lazy val scala212 = project
  .configs(IntegrationTest)
  .settings(
    Defaults.itSettings,
    unmanagedSources.in(Compile, scalafix) :=
      unmanagedSources
        .in(Compile)
        .value
        .filterNot(_.getAbsolutePath.contains("IgnoreMe")),
    scalaVersion := Versions.scala212,
    scalafixSettings
  )
  .settings(scalafixConfigSettings(IntegrationTest): _*)

lazy val javaProject = project.settings(
  scalaVersion := Versions.scala212,
  scalafixSettings
)
