inThisBuild(
  List(
    scalaVersion := "2.12.18",
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision
  )
)

lazy val example = project
  .settings(
    scalacOptions += "-Ywarn-unused"
  )
