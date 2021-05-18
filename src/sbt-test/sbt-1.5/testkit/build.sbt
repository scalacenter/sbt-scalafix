lazy val V = _root_.scalafix.sbt.BuildInfo

lazy val rulesCrossVersions = Seq(V.scala213, V.scala212, V.scala211)
lazy val scala3Version = "3.0.0"

inThisBuild(
  List(
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision
  )
)

lazy val rules = projectMatrix
  .settings(
    libraryDependencies += "ch.epfl.scala" %% "scalafix-core" % V.scalafixVersion
  )
  .defaultAxes(VirtualAxis.jvm)
  .jvmPlatform(rulesCrossVersions)

lazy val input = projectMatrix
  .defaultAxes(VirtualAxis.jvm)
  .jvmPlatform(
    scalaVersions = rulesCrossVersions,
    settings = Seq(
      scalacOptions += "-P:semanticdb:synthetics:on"
    )
  )
  .jvmPlatform(scalaVersions = Seq(scala3Version))

lazy val output = projectMatrix
  .defaultAxes(VirtualAxis.jvm)
  .jvmPlatform(scalaVersions = rulesCrossVersions :+ scala3Version)

lazy val testsAggregate = Project("tests", file("tests/aggregate"))
  .aggregate(tests.projectRefs: _*)

lazy val tests = projectMatrix
  .settings(
    libraryDependencies += "ch.epfl.scala" % "scalafix-testkit" % V.scalafixVersion % Test cross CrossVersion.full,
    scalafixTestkitOutputSourceDirectories :=
      TestkitTargetAxis
        .resolve(output, Compile / unmanagedSourceDirectories)
        .value,
    scalafixTestkitInputSourceDirectories :=
      TestkitTargetAxis
        .resolve(input, Compile / unmanagedSourceDirectories)
        .value,
    scalafixTestkitInputClasspath :=
      TestkitTargetAxis.resolve(input, Compile / fullClasspath).value,
    scalafixTestkitInputScalacOptions :=
      TestkitTargetAxis.resolve(input, Compile / scalacOptions).value,
    scalafixTestkitInputScalaVersion :=
      TestkitTargetAxis.resolve(input, Compile / scalaVersion).value
  )
  .defaultAxes(
    rulesCrossVersions.map(VirtualAxis.scalaABIVersion) :+ VirtualAxis.jvm: _*
  )
  .customRow(
    scalaVersions = Seq(V.scala212),
    axisValues = Seq(TestkitTargetAxis(scala3Version), VirtualAxis.jvm),
    settings = Seq()
  )
  .customRow(
    scalaVersions = Seq(V.scala213),
    axisValues = Seq(TestkitTargetAxis(V.scala213), VirtualAxis.jvm),
    settings = Seq()
  )
  .customRow(
    scalaVersions = Seq(V.scala212),
    axisValues = Seq(TestkitTargetAxis(V.scala212), VirtualAxis.jvm),
    settings = Seq()
  )
  .customRow(
    scalaVersions = Seq(V.scala211),
    axisValues = Seq(TestkitTargetAxis(V.scala211), VirtualAxis.jvm),
    settings = Seq()
  )
  .dependsOn(rules)
  .enablePlugins(ScalafixTestkitPlugin)
