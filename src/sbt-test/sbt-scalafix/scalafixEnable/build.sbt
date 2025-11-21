val V = _root_.scalafix.sbt.BuildInfo

lazy val config = project.settings(
  scalafixScalaBinaryVersion := scalaBinaryVersion.value, // to support old scalafix-interface
  scalaVersion := "2.13.18"
)

lazy val unsupported = project.settings(
  scalafixScalaBinaryVersion := scalaBinaryVersion.value, // to support old scalafix-interface
  // 2.11.x is not supported
  scalaVersion := "2.11.12"
)

lazy val bumpScala = project.settings(
  scalafixScalaBinaryVersion := scalaBinaryVersion.value, // to support old scalafix-interface
  // semanticdb-scalac_2.12.0 was never available
  scalaVersion := "2.12.0"
)

lazy val downgradeScalameta = project.settings(
  scalafixScalaBinaryVersion := scalaBinaryVersion.value, // to support old scalafix-interface
  // semanticdb-scalac_2.12.4 no longer available after 4.1.9
  scalaVersion := "2.12.4"
)

lazy val upgradeScalameta = project.settings(
  scalafixScalaBinaryVersion := scalaBinaryVersion.value, // to support old scalafix-interface
  // semanticdb-scalac_2.12.15 not yet available in 4.4.10, became available as of 4.4.28
  scalaVersion := "2.12.15"
)

lazy val available = project.settings(
  scalafixScalaBinaryVersion := scalaBinaryVersion.value, // to support old scalafix-interface
  // semanticdb-scalac_2.13.4 available in 4.4.10
  scalaVersion := "2.13.4",
  crossScalaVersions := Seq("2.12.15")
)

import _root_.scalafix.internal.sbt.Compat._
TaskKey[Unit]("check") := Def.uncached {
  assert((unsupported / semanticdbEnabled).value == false)
  assert((unsupported / scalaVersion).value == "2.11.12")
  assert((unsupported / Compile / compile / scalacOptions).value.isEmpty)

  assert((bumpScala / semanticdbEnabled).value == true)
  assert((bumpScala / scalaVersion).value == V.scala212)
  assert(
    (bumpScala / semanticdbCompilerPlugin).value.revision == V.scalametaVersion
  )
  assert(
    (bumpScala / Compile / compile / scalacOptions).value
      .count(_ == "-Yrangepos") == 1
  )

  assert((downgradeScalameta / semanticdbEnabled).value == true)
  assert((downgradeScalameta / scalaVersion).value == "2.12.4")
  assert(
    (downgradeScalameta / semanticdbCompilerPlugin).value.revision == "4.1.9"
  )
  assert(
    (downgradeScalameta / Compile / compile / scalacOptions).value
      .count(_ == "-Yrangepos") == 1
  )

  assert((upgradeScalameta / semanticdbEnabled).value == true)
  assert((upgradeScalameta / scalaVersion).value == "2.12.15")
  assert(
    (upgradeScalameta / semanticdbCompilerPlugin).value.revision == "4.4.28"
  )
  assert(
    (upgradeScalameta / Compile / compile / scalacOptions).value
      .count(_ == "-Yrangepos") == 1
  )

  assert((available / semanticdbEnabled).value == true)
  assert((available / scalaVersion).value == "2.13.4")
  assert((available / semanticdbCompilerPlugin).value.revision == "4.4.10")
  assert(
    (available / Test / compile / scalacOptions).value
      .count(_ == "-Yrangepos") == 1
  )
}

TaskKey[Unit]("checkVersion") := Def.uncached {
  assert((available / scalaVersion).value == "2.12.15")
}
