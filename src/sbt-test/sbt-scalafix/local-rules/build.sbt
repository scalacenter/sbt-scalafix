import _root_.scalafix.sbt.{BuildInfo => Versions}

inThisBuild(
  List(
    scalafixDependencies := List(
      // CollectHead
      "com.github.xuwei-k" %% "scalafix-rules" % "0.6.5"
    ),
    resolvers += Resolver.sonatypeRepo("snapshots"),
    // out of sync with scalafix.sbt.BuildInfo.scala213 on purpose
    scalaVersion := "2.13.11",
    Compat.allowUnsafeScalaLibUpgrade := true
  )
)

// LocalSyntacticRule
val rules = project
  .disablePlugins(ScalafixPlugin)
  .settings(
    libraryDependencies += "ch.epfl.scala" %% "scalafix-core" % Versions.scalafixVersion,
    libraryDependencies += "joda-time" % "joda-time" % "2.10.6"
  )

val service = project
  .dependsOn(rules % ScalafixConfig)
  .settings(
    // SyntacticRule
    libraryDependencies += "ch.epfl.scala" %% "example-scalafix-rule" % "3.0.0" % ScalafixConfig
  )

// SameProjectSyntacticRule
val sameproject = project
  .settings(
    libraryDependencies += "ch.epfl.scala" %% "scalafix-core" % Versions.scalafixVersion % ScalafixConfig
  )
