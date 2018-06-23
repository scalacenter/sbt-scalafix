inThisBuild(
  List(
    version ~= { old =>
      if (System.getenv("CI") == null) "SNAPSHOT"
      else old
    },
    onLoadMessage := s"Welcome to sbt-scalafix ${version.value}",
    scalaVersion := "2.12.6",
    organization := "ch.epfl.scala",
    homepage := Some(url("https://github.com/scalacenter/sbt-scalafix")),
    licenses := List(
      "Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")
    ),
    developers := List(
      Developer(
        "olafurpg",
        "Ólafur Páll Geirsson",
        "olafurpg@gmail.com",
        url("https://geirsson.com")
      )
    )
  )
)

skip in publish := true

lazy val plugin = project
  .settings(
    moduleName := "sbt-scalafix",
    sbtPlugin := true,
    scriptedBufferLog := false,
    scriptedLaunchOpts ++= Seq(
      "-Xmx2048M",
      s"-Dplugin.version=${version.value}"
    ),
    libraryDependencies ++= List(
      "ch.epfl.scala" % "scalafix-cli" % "0.6.0-M9" cross CrossVersion.full,
      "org.scalatest" %% "scalatest" % "3.0.5" % Test
    )
  )
