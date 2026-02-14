import _root_.scalafix.sbt.{BuildInfo => Versions}

inThisBuild(
  List(
    scalaVersion := Versions.scala212,
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    scalacOptions += "-Ywarn-unused", // for RemoveUnused
    scalafixDependencies += "com.geirsson" %% "example-scalafix-rule" % "1.2.0",
    resolvers +=
      // for retrieving SNAPSHOTS of `scalafix-interfaces`
      MavenRepository(
        "sonatype-central-maven-snapshots",
        "https://central.sonatype.com/repository/maven-snapshots/"
      ),
    // On recent sbt versions, make sure unmanagedSources do not silently
    // exclude 000-chmoded files (because input files are stamped with
    // Hash by default), as it causes false positives in the test suite
    unmanagedSources / inputFileStamper := sbt.nio.FileStamper.LastModified
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
