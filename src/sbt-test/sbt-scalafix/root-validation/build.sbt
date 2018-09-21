inThisBuild(
  List(
    scalaVersion := "2.12.6"
  )
)

lazy val root = project
  .in(file("."))
  .aggregate(lib)

lazy val lib = project
  .in(file("lib"))
  .settings(
    addCompilerPlugin(scalafixSemanticdb),
    scalacOptions ++= List(
      "-Ywarn-unused",
      "-Yrangepos"
    )
  )
