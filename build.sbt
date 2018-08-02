inThisBuild(
  List(
    version ~= { old =>
      if (System.getenv("CI") == null) "SNAPSHOT"
      else old
    },
    onLoadMessage := s"Welcome to sbt-scalafix ${version.value}",
    scalaVersion := {
      if (sbtVersion.in(pluginCrossBuild).value.startsWith("0.13")) "2.10.6"
      else "2.12.6"
    },
    crossSbtVersions := List("0.13.17", "1.2.0"),
    resolvers += Resolver.sonatypeRepo("releases"),
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

// For some reason I'm not motivated to debug, ^publishSigned did not work
// as intended when I tried it.
commands += Command.command("release-all") { s =>
  "^^1.2.0 publishSigned" ::
    "^^0.13.17 publishSigned" ::
    s
}
commands += Command.command("release-snapshot-all") { s =>
  "^^ 1.2.0 publish" ::
    "^^ 0.13.17 publish" ::
    s
}
commands += Command.command("test-all") { s =>
  "^^ 1.2.0 ;test;scripted" ::
    "^^ 0.13.17 ;test;scripted" ::
    s
}

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
      "org.eclipse.jgit" % "org.eclipse.jgit" % "4.5.4.201711221230-r",
      "com.geirsson" %% "coursier-small" % "1.0.0-M2",
      "org.scalatest" %% "scalatest" % "3.0.5" % Test
    )
  )
