inThisBuild(
  List(
    Test / parallelExecution := false,
    scalafixDependencies := List(
      // Custom rule published to Maven Central https://github.com/scalacenter/example-scalafix-rule
      "ch.epfl.scala" %% "example-scalafix-rule" % "1.4.0"
    )
  )
)
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
    "bjaglin",
    "Brice Jaglin",
    "bjaglin@teads.tv",
    url("https://github.com/bjaglin")
  ),
  Developer(
    "olafurpg",
    "Ólafur Páll Geirsson",
    "olafurpg@gmail.com",
    url("https://geirsson.com")
  )
)

commands += Command.command("ci-windows") { s =>
  "testOnly -- -l SkipWindows" ::
    "scripted sbt-*/*" ::
    s
}

// Dependencies
resolvers += Resolver.sonatypeRepo("public")
libraryDependencies ++= Dependencies.all
libraryDependencies ++= List(
  "com.lihaoyi" %% "fansi" % "0.2.6" % Test,
  "org.scalatest" %% "scalatest" % "3.2.9" % Test
)

scalaVersion := "2.13.6"

// keep this as low as possible to avoid running into binary incompatibility such as https://github.com/sbt/sbt/issues/5049
pluginCrossBuild / sbtVersion := "1.2.1"

// first release that can build 2.13 (as it bring a Zinc version with a compiler-bridge published for 2.13)
scriptedSbt := "1.2.7"

libraryDependencies += compilerPlugin(scalafixSemanticdb)

scalacOptions ++= List("-Ywarn-unused", "-Yrangepos")

scalacOptions ++= List(
  "-target:jvm-1.8",
  "-Xfatal-warnings",
  "-Xlint"
)

// Scripted
enablePlugins(ScriptedPlugin)
sbtPlugin := true
scriptedBufferLog := false
scriptedLaunchOpts ++= Seq(
  "-Xmx2048M",
  s"-Dplugin.version=${version.value}",
  "-Dsbt-scalafix.uselastmodified=true" // the caching scripted relies on sbt-scalafix only checking file attributes, not content
)
