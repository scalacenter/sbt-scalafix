val V = _root_.scalafix.sbt.BuildInfo

// 2.10 is not supported, scalafix is not enabled
lazy val scala210 = project.settings(
  scalaVersion := "2.10.4",
  libraryDependencies := Nil,
  scalacOptions := Nil
)

// 2.12.0 is supported but the version is overidden
lazy val overridesSettings = project.settings(
  scalaVersion := "2.12.0",
  libraryDependencies := Nil,
  scalacOptions := Nil
)

// 2.11.x is supported
lazy val scala211 = project.settings(
  scalaVersion := V.scala211
)

// 2.12.x is supported
lazy val scala212 = project.settings(
  scalaVersion := V.scala212
)

// 2.13.x is supported
lazy val scala213 = project.settings(
  scalaVersion := V.scala213
)

TaskKey[Unit]("check") := {
  // nothing should change for the 2.10 project
  assert((scala210 / scalaVersion).value == "2.10.4")
  assert((scala210 / libraryDependencies).value.isEmpty)
  assert((scala210 / Compile / compile / scalacOptions).value.isEmpty)

  // 2.12.0 should be overidden to 2.12.X
  assert((overridesSettings / scalaVersion).value == V.scala212)
  assert((overridesSettings / libraryDependencies).value.nonEmpty)
  assert(
    (overridesSettings / Compile / compile / scalacOptions).value
      .contains("-Yrangepos")
  )

  assert(
    (scala211 / Compile / compile / scalacOptions).value
      .count(_ == "-Yrangepos") == 1
  )
  assert(
    (scala212 / Compile / compile / scalacOptions).value
      .count(_ == "-Yrangepos") == 1
  )
  assert(
    (scala213 / Test / compile / scalacOptions).value
      .count(_ == "-Yrangepos") == 1
  )
}
