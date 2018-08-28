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

commands += Command.command("ci-windows") { s =>
  "testOnly -- -l SkipWindows" ::
    "scripted" ::
    s
}

def scalafixVersion: String = sys.env.get("TRAVIS_TAG") match {
  case Some(v) if v.nonEmpty => v.stripPrefix("v")
  case _ => "0.6.0-M17"
}

// Dependencies
resolvers += Resolver.sonatypeRepo("releases")
libraryDependencies ++= List(
  "org.eclipse.jgit" % "org.eclipse.jgit" % "4.5.4.201711221230-r",
  "ch.epfl.scala" % "scalafix-interfaces" % scalafixVersion,
  // coursier-small provides a binary stable API around Coursier making sure that
  // sbt-scalafix doesn't conflict with the user's installed version of sbt-coursier.
  // Details: https://github.com/olafurpg/coursier-small
  "com.geirsson" %% "coursier-small" % "1.0.0-M4",
  "com.lihaoyi" %% "fansi" % "0.2.5" % Test,
  "org.scalatest" %% "scalatest" % "3.0.5" % Test
)

// Cross-building settings (see https://github.com/sbt/sbt/issues/3473#issuecomment-325729747)
crossScalaVersions := Seq("2.10.7", "2.12.6")
sbtVersion in pluginCrossBuild := {
  scalaBinaryVersion.value match {
    case "2.10" => "0.13.17"
    case "2.12" => "1.2.1"
  }
}
scalacOptions ++= List(
  "-target:jvm-1.8"
)

// Build info
enablePlugins(BuildInfoPlugin)
buildInfoKeys := Seq[BuildInfoKey](
  "scala212" -> "2.12.6",
  "scala211" -> "2.11.12",
  "scalafixVersion" -> scalafixVersion,
  "scalametaVersion" -> "4.0.0-M8"
)
buildInfoPackage := "scalafix.internal.sbtscalafix"

// Scripted
enablePlugins(ScriptedPlugin)
sbtPlugin := true
scriptedBufferLog := false
scriptedLaunchOpts ++= Seq(
  "-Xmx2048M",
  s"-Dplugin.version=${version.value}"
)
