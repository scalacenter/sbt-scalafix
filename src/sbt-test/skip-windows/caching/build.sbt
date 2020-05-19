import _root_.scalafix.sbt.{BuildInfo => Versions}

inThisBuild(
  List(
    scalaVersion := Versions.scala212,
    addCompilerPlugin(scalafixSemanticdb),
    scalacOptions += "-Yrangepos",
    scalacOptions += "-Ywarn-unused", // for RemoveUnused
    scalafixCaching := true,
    scalafixDependencies += "com.geirsson" %% "example-scalafix-rule" % "1.2.0"
  )
)

val rules = project
  .disablePlugins(ScalafixPlugin)
  .settings(
    libraryDependencies += "ch.epfl.scala" %% "scalafix-core" % Versions.scalafixVersion
  )

val root = project
  .in(file("."))
  .dependsOn(rules % ScalafixConfig)
