onLoadMessage := s"Welcome to sbt-scalafix ${version.value} built with Scala ${scalaVersion.value}"

// Publish settings
organization := "ch.epfl.scala"
homepage := Some(uri("https://github.com/scalacenter/sbt-scalafix"))
licenses += "Apache-2.0" -> uri("http://www.apache.org/licenses/LICENSE-2.0")
developers := List(
  Developer(
    "bjaglin",
    "Brice Jaglin",
    "bjaglin@gmail.com",
    uri("https://github.com/bjaglin")
  ),
  Developer(
    "olafurpg",
    "Ólafur Páll Geirsson",
    "olafurpg@gmail.com",
    uri("https://geirsson.com")
  )
)

lazy val latestScalafixInterfacesSnapshotVersion = {
  val metadataUrl =
    "https://central.sonatype.com/repository/maven-snapshots/ch/epfl/scala/scalafix-interfaces/maven-metadata.xml"

  val snapshotVersionPattern = "<version>([^<]+)</version>".r
  val metadata = scala.io.Source.fromURL(metadataUrl, "UTF-8").mkString
  snapshotVersionPattern
    .findAllMatchIn(metadata)
    .map(_.group(1))
    .filter(_.endsWith("-SNAPSHOT"))
    .toList
    .last
}

lazy val scala212 = "2.12.21"
lazy val scala3 = "3.8.4"

lazy val plugin = (projectMatrix in file("plugin"))
  .settings(
    sbtPlugin := true,
    name := "sbt-scalafix",
    resolvers += Resolver.sonatypeCentralSnapshots,
    libraryDependencies ++= Dependencies.all,
    libraryDependencies ++= List(
      "com.lihaoyi" %% "fansi" % "0.5.1" % Test,
      "org.scalatest" %% "scalatest" % "3.2.20" % Test
    )
  )
  .defaultAxes(VirtualAxis.jvm)
  .jvmPlatform(
    scalaVersions = List(scala212),
    settings = List(
      pluginCrossBuild / sbtVersion := "1.4.0", // keep low for max compatibility
      scalacOptions ++= List(
        "-release:8",
        "-Xsource:3",
        "-Ywarn-unused",
        "-Xlint",
        "-Xfatal-warnings"
      )
    )
  )
  .jvmPlatform(
    scalaVersions = List(scala3),
    settings = List(
      pluginCrossBuild / sbtVersion := "2.0.0", // keep low for max compatibility
      scalacOptions ++= List(
        "-release:17",
        "-Wunused:all",
        "-Werror"
      )
    )
  )

lazy val sbt1 = "1.13.1"
lazy val sbt20 = "2.0.12"
lazy val sbt21 = "2.1.0-RC3"

import ScriptedAxis._
lazy val scripted = (projectMatrix in file("scripted"))
  .enablePlugins(ScriptedPlugin)
  .dependsOn(plugin)
  .settings(
    publish / skip := true,
    publishLocal / skip := true,
    scriptedBufferLog := false,
    scriptedBatchExecution := true,
    scriptedParallelInstances := 2,
    scriptedLaunchOpts ++= List(
      "-Xmx2048M",
      s"-Dplugin.version=${version.value}",
      s"-Dsbt.version=${scriptedSbt.value}",
      s"-Dscalafix.interfaces.snapshot.version=$latestScalafixInterfacesSnapshotVersion",
      "-Dsbt-scalafix.uselastmodified=true" // the caching scripted relies on file attributes, not content
    )
  )
  .defaultAxes(
    VirtualAxis.jvm,
    VirtualAxis.scalaABIVersion(scala212),
    VirtualAxis.scalaABIVersion(scala3)
  )
  .jvmPlatformScripted(
    scalaVersion = scala212,
    scriptedSbtVersion = List(sbt1)
  )
  .jvmPlatformScripted(
    scalaVersion = scala3,
    scriptedSbtVersion = List(sbt20, sbt21)
  )

// dogfooding
semanticdbEnabled := true
semanticdbVersion := scalafixSemanticdb.revision
scalafixDependencies := List(
  // Custom rule published to Maven Central https://github.com/scalacenter/example-scalafix-rule
  "ch.epfl.scala" %% "example-scalafix-rule" % "1.4.0"
)
