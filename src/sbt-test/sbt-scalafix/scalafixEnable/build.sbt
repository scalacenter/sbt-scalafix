val V = _root_.scalafix.sbt.BuildInfo

lazy val config = project.settings(
  scalaVersion := "2.13.16"
)

lazy val unsupported = project.settings(
  // 2.10.x is not supported
  scalaVersion := "2.10.7"
)

lazy val available = project.settings(
  // semanticdb-scalac_2.13.4 available in 4.4.10
  scalaVersion := "2.13.4",
  crossScalaVersions := Seq("2.12.15")
)

TaskKey[Unit]("check") := {
  assert((unsupported / semanticdbEnabled).value == false)
  assert((unsupported / Compile / compile / scalacOptions).value.isEmpty)

  assert((available / semanticdbEnabled).value == true)
  assert(
    (available / Test / compile / scalacOptions).value
      .count(_ == "-Yrangepos") == 1
  )
}

TaskKey[Unit]("checkVersion") := {
  assert((available / scalaVersion).value == "2.12.15")
}
