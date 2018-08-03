onLoadMessage := s"Welcome to sbt-scalafix ${version.value}"
moduleName := "sbt-scalafix"

// Publish settings
organization := "ch.epfl.scala"
homepage := Some(url("https://github.com/scalacenter/sbt-scalafix"))
licenses := List(
  "Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")
)
developers := List(
  Developer(
    "olafurpg",
    "Ólafur Páll Geirsson",
    "olafurpg@gmail.com",
    url("https://geirsson.com")
  )
)

// Dependencies
resolvers += Resolver.sonatypeRepo("releases")
libraryDependencies ++= List(
  "org.eclipse.jgit" % "org.eclipse.jgit" % "4.5.4.201711221230-r",
  // coursier-small provides a binary stable API around Coursier making sure that
  // sbt-scalafix doesn't conflict with the user's installed version of sbt-coursier.
  // Details: https://github.com/olafurpg/coursier-small
  "com.geirsson" %% "coursier-small" % "1.0.0-M4",
  "org.scalatest" %% "scalatest" % "3.0.5" % Test
)

// Cross-building settings (see https://github.com/sbt/sbt/issues/3473#issuecomment-325729747)
crossScalaVersions := Seq("2.10.7", "2.12.6")
sbtVersion in pluginCrossBuild := {
  scalaBinaryVersion.value match {
    case "2.10" => "0.13.17"
    case "2.12" => "1.2.0"
  }
}

// Scripted
enablePlugins(ScriptedPlugin)
sbtPlugin := true
scriptedBufferLog := false
scriptedLaunchOpts ++= Seq(
  "-Xmx2048M",
  s"-Dplugin.version=${version.value}"
)
