onLoadMessage := s"Welcome to sbt-scalafix ${version.value} built with Scala ${scalaVersion.value}"

// Publish settings
organization := "ch.epfl.scala"
homepage := Some(url("https://github.com/scalacenter/sbt-scalafix"))
licenses += "Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")
developers := List(
  Developer(
    "bjaglin",
    "Brice Jaglin",
    "bjaglin@gmail.com",
    url("https://github.com/bjaglin")
  ),
  Developer(
    "olafurpg",
    "Ólafur Páll Geirsson",
    "olafurpg@gmail.com",
    url("https://geirsson.com")
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
  .enablePlugins(SbtPlugin)
  .settings(
    name := "sbt-scalafix",
    resolvers += Resolver.sonatypeCentralSnapshots,
    libraryDependencies ++= Dependencies.all,
    libraryDependencies ++= List(
      "com.lihaoyi" %% "fansi" % "0.5.1" % Test,
      "org.scalatest" %% "scalatest" % "3.2.20" % Test
    ),
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
  .defaultAxes(VirtualAxis.jvm)
  .jvmPlatform(
    scalaVersions = List(scala212),
    settings = List(
      // Keep this low to avoid binary incompatibilities such as https://github.com/sbt/sbt/issues/5049.
      pluginCrossBuild / sbtVersion := "1.4.0",
      scriptedSbt := {
        val jdk = System.getProperty("java.specification.version").toDouble
        if (jdk >= 21)
          "1.9.0" // first sbt 1.x release that supports JDK 21
        else
          (pluginCrossBuild / sbtVersion).value
      },
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
      pluginCrossBuild / sbtVersion := "2.0.7",
      scalacOptions ++= List(
        "-release:17",
        "-Wunused:all",
        "-Werror"
      ),
      // https://github.com/scalacenter/sbt-scalafix/pull/440#discussion_r2008037490
      scriptedBatchExecution := false
    )
  )

// dogfooding
semanticdbEnabled := true
semanticdbVersion := scalafixSemanticdb.revision
scalafixDependencies := List(
  // Custom rule published to Maven Central https://github.com/scalacenter/example-scalafix-rule
  "ch.epfl.scala" %% "example-scalafix-rule" % "1.4.0"
)
