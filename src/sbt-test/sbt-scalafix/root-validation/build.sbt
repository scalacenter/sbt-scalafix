inThisBuild(
  List(
    scalaVersion := "2.12.20"
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
