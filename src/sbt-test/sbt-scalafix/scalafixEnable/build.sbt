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
  assert(scalaVersion.in(scala210).value == "2.10.4")
  assert(libraryDependencies.in(scala210).value.isEmpty)
  assert(scalacOptions.in(scala210, Compile, compile).value.isEmpty)

  // 2.12.0 should be overidden to 2.12.X
  assert(scalaVersion.in(overridesSettings).value == V.scala212)
  assert(libraryDependencies.in(overridesSettings).value.nonEmpty)
  assert(
    scalacOptions
      .in(overridesSettings, Compile, compile)
      .value
      .contains("-Yrangepos")
  )

  assert(
    scalacOptions
      .in(scala211, Compile, compile)
      .value
      .count(_ == "-Yrangepos") == 1
  )
  assert(
    scalacOptions
      .in(scala212, Compile, compile)
      .value
      .count(_ == "-Yrangepos") == 1
  )
  assert(
    scalacOptions
      .in(scala213, Test, compile)
      .value
      .count(_ == "-Yrangepos") == 1
  )
}
