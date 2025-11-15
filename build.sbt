// dogfooding
semanticdbEnabled := true
scalafixDependencies := List(
  // Custom rule published to Maven Central https://github.com/scalacenter/example-scalafix-rule
  "ch.epfl.scala" %% "example-scalafix-rule" % "1.4.0"
)

onLoadMessage := s"Welcome to sbt-scalafix ${version.value} built with Scala ${scalaVersion.value}"
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

commands += Command.command("test-skip-windows") { s =>
  "testOnly -- -l SkipWindows" ::
    s
}

// Dependencies
resolvers += Resolver.sonatypeCentralSnapshots
libraryDependencies ++= Dependencies.all
libraryDependencies ++= List(
  "com.lihaoyi" %% "fansi" % "0.5.1" % Test,
  "org.scalatest" %% "scalatest" % "3.2.19" % Test
)

lazy val scala212 = "2.12.20"
lazy val scala3 = "3.7.4"

scalaVersion := scala212
crossScalaVersions := Seq(scala212, scala3)

// keep this as low as possible to avoid running into binary incompatibility such as https://github.com/sbt/sbt/issues/5049
pluginCrossBuild / sbtVersion := {
  scalaBinaryVersion.value match {
    case "2.12" =>
      "1.4.0"
    case _ =>
      "2.0.0-RC6"
  }
}

scriptedSbt := {
  val jdk = System.getProperty("java.specification.version").toDouble

  if (jdk >= 21)
    Ordering[String].max(
      (pluginCrossBuild / sbtVersion).value,
      "1.9.0" // first release that supports JDK21
    )
  else
    (pluginCrossBuild / sbtVersion).value
}

scalacOptions ++= {
  scalaBinaryVersion.value match {
    case "2.12" =>
      List(
        "-Ywarn-unused",
        "-Xlint",
        "-Xfatal-warnings"
      )
    case _ =>
      List(
        "-Wunused:all",
        "-Werror"
      )
  }
}

// Scripted
enablePlugins(ScriptedPlugin)
sbtPlugin := true
scriptedBufferLog := false
scriptedBatchExecution := {
  scalaBinaryVersion.value match {
    case "2.12" => true
    case _ => false
  }
}
scriptedParallelInstances := 2
scriptedLaunchOpts ++= Seq(
  "-Xmx2048M",
  s"-Dplugin.version=${version.value}",
  s"-Dsbt.version=${scriptedSbt.value}",
  "-Dsbt-scalafix.uselastmodified=true" // the caching scripted relies on sbt-scalafix only checking file attributes, not content
)
