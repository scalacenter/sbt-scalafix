import _root_.scalafix.sbt.{BuildInfo => Versions}

inThisBuild(
  List(
    scalafixDependencies := List(
      // Custom rule cross-published to Maven Central https://github.com/scalacenter/example-scalafix-rule
      "ch.epfl.scala" %% "example-scalafix-rule" % "1.6.0"
    ),
    resolvers += Resolver.sonatypeRepo("snapshots"),
    scalaVersion := "2.13.0", // out of sync with scalafix.sbt.BuildInfo.scala213 on purpose
    scalafixScalaBinaryVersion :=
      // this should be the default in sbt-scalafix 1.0
      CrossVersion.binaryScalaVersion(scalaVersion.value)
  )
)

val rules = project
  .disablePlugins(ScalafixPlugin)
  .settings(
    libraryDependencies += "ch.epfl.scala" %% "scalafix-core" % Versions.scalafixVersion,
    libraryDependencies += "joda-time" % "joda-time" % "2.10.6"
  )

val service = project
  .dependsOn(rules % ScalafixConfig)
  .settings(
    libraryDependencies += "com.nequissimus" %% "sort-imports" % "0.5.2" % ScalafixConfig
  )

val sameproject = project
  .settings(
    libraryDependencies += "ch.epfl.scala" %% "scalafix-core" % Versions.scalafixVersion % ScalafixConfig
  )
