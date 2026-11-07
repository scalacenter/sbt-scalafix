inThisBuild(
  List(
    scalaVersion := "2.12.21"
  )
)

lazy val root = project
  .in(file("."))
  .aggregate(lib)

lazy val lib = project
  .in(file("lib"))
  .settings(
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    scalacOptions += "-Ywarn-unused"
  )
