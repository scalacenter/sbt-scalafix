inThisBuild(
  List(
    parallelExecution in Test := false,
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
  "org.scalatest" %% "scalatest" % "3.2.0" % Test
)

// Cross-building settings (see https://github.com/sbt/sbt/issues/3473#issuecomment-325729747)
def scala212 = "2.12.11"
def scala210 = "2.10.7"
scalaVersion := scala212
crossScalaVersions := Seq(scala212, scala210)
sbtVersion in pluginCrossBuild := {
  // keep this as low as possible to avoid running into binary incompatibility such as https://github.com/sbt/sbt/issues/5049
  scalaBinaryVersion.value match {
    case "2.10" => "0.13.17"
    case "2.12" => "1.2.1"
  }
}
scriptedSbt := {
  // first releases that can build 2.13 (as they bring a Zinc version with a compiler-bridge published for 2.13)
  scalaBinaryVersion.value match {
    case "2.10" => "0.13.18"
    case "2.12" => "1.2.7"
  }
}
libraryDependencies ++= {
  scalaBinaryVersion.value match {
    case "2.12" =>
      List(compilerPlugin(scalafixSemanticdb))
    case _ =>
      List()
  }
}
scalacOptions ++= {
  scalaBinaryVersion.value match {
    case "2.12" =>
      List("-Ywarn-unused", "-Yrangepos")
    case _ =>
      List()
  }
}
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
  s"-Dplugin.version=${version.value}"
)
