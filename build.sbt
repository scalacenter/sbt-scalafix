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

lazy val scala212 = "2.12.20"
lazy val scala3 = "3.7.2"
lazy val plugin = (projectMatrix in file("plugin"))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "sbt-scalafix",
    // Dependencies
    resolvers ++= Resolver.sonatypeOssRepos("public"),
    libraryDependencies ++= Dependencies.all,
    libraryDependencies ++= List(
      "com.lihaoyi" %% "fansi" % "0.5.0" % Test,
      "org.scalatest" %% "scalatest" % "3.2.19" % Test
    ),
    // Scripted settings
    scriptedBufferLog := false,
    scriptedBatchExecution := true,
    scriptedParallelInstances := 2,
    scriptedLaunchOpts ++= List(
      "-Xmx2048M",
      s"-Dplugin.version=${version.value}",
      "-Dsbt-scalafix.uselastmodified=true" // the caching scripted relies on sbt-scalafix only checking file attributes, not content
    )
  )
  .defaultAxes(VirtualAxis.jvm)
  .jvmPlatform(
    scalaVersions = List(scala212),
    settings = List(
      // keep this as low as possible to avoid running into binary incompatibility such as https://github.com/sbt/sbt/issues/5049
      pluginCrossBuild / sbtVersion := "1.4.0",
      scriptedSbt := {
        val jdk = System.getProperty("java.specification.version").toDouble

        if (jdk >= 21)
          Ordering[String].max(
            (pluginCrossBuild / sbtVersion).value,
            "1.9.0" // first release that supports JDK21
          )
        else
          (pluginCrossBuild / sbtVersion).value
      },
      scalacOptions ++= List(
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
      scalacOptions ++= List(
        "-Wunused:all",
        "-Werror"
      ),
      // https://github.com/scalacenter/sbt-scalafix/pull/440#discussion_r2008037490
      scriptedBatchExecution := false
    )
  )

// dogfooding
semanticdbEnabled := true
scalafixDependencies := List(
  // Custom rule published to Maven Central https://github.com/scalacenter/example-scalafix-rule
  "ch.epfl.scala" %% "example-scalafix-rule" % "1.4.0"
)
