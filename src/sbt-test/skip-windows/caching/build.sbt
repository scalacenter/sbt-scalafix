import _root_.scalafix.sbt.{BuildInfo => Versions}

inThisBuild(
  List(
    scalaVersion := Versions.scala212,
    addCompilerPlugin(scalafixSemanticdb),
    scalacOptions += "-Yrangepos",
    scalacOptions += "-Ywarn-unused", // for RemoveUnused
    scalafixDependencies += "com.geirsson" %% "example-scalafix-rule" % "1.2.0",
    resolvers +=
      // for retrieving SNAPSHOTS of `scalafix-interfaces`
      Resolver.sonatypeRepo("snapshots")
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
