val V = _root_.scalafix.sbt.BuildInfo

// 2.10 is not supported
lazy val scala210 = project.settings(
  scalaVersion := "2.10.4",
  libraryDependencies := Nil,
  scalacOptions := Nil
)

// 2.11.x is supported
lazy val scala211_old = project.settings(
  // semanticdb-scalac_2.11.0 was never available
  scalaVersion := "2.11.0"
)

// 2.11.x is supported
lazy val scala211 = project.settings(
  // semanticdb-scalac_2.11.11 no longer available after 4.1.9
  scalaVersion := "2.11.11"
)

// 2.12.x is supported
lazy val scala212 = project.settings(
  // semanticdb-scalac_2.12.15 not yet available in 4.4.10, became available as of 4.4.28
  scalaVersion := "2.12.15"
)

// 2.13.x is supported
lazy val scala213 = project.settings(
  // semanticdb-scalac_2.13.4 available in 4.4.10, became available as of 4.4.0
  scalaVersion := "2.13.4",
  crossScalaVersions := Seq("2.12.15")
)

TaskKey[Unit]("check") := {
  assert((scala210 / semanticdbEnabled).value == false)
  assert((scala210 / scalaVersion).value == "2.10.4")
  assert((scala210 / Compile / compile / scalacOptions).value.isEmpty)

  assert((scala211_old / semanticdbEnabled).value == true)
  assert((scala211_old / scalaVersion).value == "2.11.12")
  assert((scala211_old / semanticdbCompilerPlugin).value.revision == "4.4.10")
  assert(
    (scala211_old / Compile / compile / scalacOptions).value
      .count(_ == "-Yrangepos") == 1
  )

  assert((scala211 / semanticdbEnabled).value == true)
  assert((scala211 / scalaVersion).value == "2.11.11")
  assert((scala211 / semanticdbCompilerPlugin).value.revision == "4.1.9")
  assert(
    (scala211 / Compile / compile / scalacOptions).value
      .count(_ == "-Yrangepos") == 1
  )

  assert((scala212 / semanticdbEnabled).value == true)
  assert((scala212 / scalaVersion).value == "2.12.15")
  assert((scala212 / semanticdbCompilerPlugin).value.revision == "4.4.28")
  assert(
    (scala212 / Compile / compile / scalacOptions).value
      .count(_ == "-Yrangepos") == 1
  )

  assert((scala213 / semanticdbEnabled).value == true)
  assert((scala213 / scalaVersion).value == "2.13.4")
  assert((scala213 / semanticdbCompilerPlugin).value.revision == "4.4.10")
  assert(
    (scala213 / Test / compile / scalacOptions).value
      .count(_ == "-Yrangepos") == 1
  )
}

TaskKey[Unit]("checkVersion") := {
  assert((scala213 / scalaVersion).value == "2.12.15")
}
