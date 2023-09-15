inThisBuild(
  List(
    scalaVersion := "2.13.12",
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision
  )
)

lazy val example = project
  .settings(
    scalacOptions += "-Ywarn-unused"
  )
