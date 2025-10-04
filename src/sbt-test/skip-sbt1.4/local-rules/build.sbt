import _root_.scalafix.sbt.{BuildInfo => Versions}

inThisBuild(
  List(
    scalafixDependencies := List(
      // CollectHead
      "com.github.xuwei-k" %% "scalafix-rules" % "0.6.5"
    ),
    resolvers += MavenRepository(
      "sonatype-central-maven-snapshots",
      "https://central.sonatype.com/repository/maven-snapshots/"
    ),
    // out of sync with scalafix.sbt.BuildInfo.scala213 on purpose
    scalaVersion := "2.13.11",
    Compat.allowUnsafeScalaLibUpgrade := true
  )
)

// LocalSyntacticRule
val rules = project
  .disablePlugins(ScalafixPlugin)
  .settings(
    libraryDependencies +=
      ("ch.epfl.scala" %% "scalafix-core" % Versions.scalafixVersion)
        .cross(CrossVersion.for3Use2_13),
    libraryDependencies += "joda-time" % "joda-time" % "2.10.6"
  )

val service = project
  .dependsOn(rules % ScalafixConfig)
  .settings(
    // SyntacticRule
    libraryDependencies +=
      ("ch.epfl.scala" %% "example-scalafix-rule" % "3.0.0")
        .cross(CrossVersion.for3Use2_13) % ScalafixConfig
  )

// SameProjectSyntacticRule
val sameproject = project
  .settings(
    libraryDependencies +=
      ("ch.epfl.scala" %% "scalafix-core" % Versions.scalafixVersion)
        .cross(CrossVersion.for3Use2_13) % ScalafixConfig,
    // Since sbt 1.10.x (https://github.com/sbt/sbt/pull/7480), scala3-library is not automatically added
    // to non-standard configurations, but is needed by the Scala 3 compiler, so it must be added explicitly
    // if no dependency brings it implicitly, which is the case here because the only dependency is for3Use2_13.
    libraryDependencies ++= {
      if (scalaBinaryVersion.value == "3")
        Seq(
          "org.scala-lang" %% "scala3-library" % scalaVersion.value % ScalafixConfig
        )
      else
        Nil
    }
  )
