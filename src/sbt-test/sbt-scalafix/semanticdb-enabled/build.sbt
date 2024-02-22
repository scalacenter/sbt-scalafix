inThisBuild(
  List(
    scalaVersion := "2.12.19",
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision
  )
)

lazy val example = project
  .settings(
    scalacOptions += "-Ywarn-unused"
  )
