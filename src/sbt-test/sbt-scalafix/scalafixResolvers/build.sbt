ThisBuild / scalafixDependencies := List(
  // Custom rule cross-published to Maven Central https://github.com/scalacenter/example-scalafix-rule
  "ch.epfl.scala" %% "example-scalafix-rule" % "4.0.0+3-e9850db5-SNAPSHOT"
)

ThisBuild / resolvers ++= Resolver.sonatypeOssRepos("snapshots")
