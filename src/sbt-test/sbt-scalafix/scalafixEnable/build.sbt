val V = _root_.scalafix.sbt.BuildInfo

// 2.10 is not supported, scalafix is not enabled
lazy val scala210 = project.settings(
  scalaVersion := "2.10.4",
  libraryDependencies := Nil,
  scalacOptions := Nil
)

// 2.12.0 is supported but the version is overidden
lazy val overridesSettings = project.settings(
  scalaVersion := "2.12.0",
  libraryDependencies := Nil,
  scalacOptions := Nil
)

// 2.11.x is supported
lazy val scala211 = project.settings(
  scalaVersion := V.scala211
)

// 2.12.x is supported
lazy val scala212 = project.settings(
  scalaVersion := V.scala212,
  scalacOptions ++= Seq(
    // generate errors on unused imports
    "-Xfatal-warnings",
    "-Ywarn-unused",
    // generate errors on procedure syntax
    "-Wconf:cat=deprecation:e",
    "-Xfuture",
    "-deprecation"
  ),
  TaskKey[Unit]("checkLastCompilationCached") := {
    val str = streams.value
    val thisProject = thisProjectRef.value

    // readText expects a fully resolved scope (no This)
    val compileIncScope = Global.in(thisProject, Compile, compileIncremental.key)
    val reader = str.readText(streamScopedKey(compileIncScope), None)
    val lines = IO.readLines(reader)

    val cachedCompilationLogEntry = "No changes"
    if (lines.forall(line => !line.contains(cachedCompilationLogEntry))) {
      lines.foreach(line => str.log.error(line))
      throw new RuntimeException("last compilation was not fully cached")
    }
  }
)

// 2.13.x is supported
lazy val scala213 = project.settings(
  scalaVersion := V.scala213
)

TaskKey[Unit]("check") := {
  // nothing should change for the 2.10 project
  assert(scalaVersion.in(scala210).value == "2.10.4")
  assert(libraryDependencies.in(scala210).value.isEmpty)
  assert(scalacOptions.in(scala210).value.isEmpty)

  // 2.12.0 should be overidden to 2.12.X
  assert(scalaVersion.in(overridesSettings).value == V.scala212)
  assert(libraryDependencies.in(overridesSettings).value.nonEmpty)
  assert(scalacOptions.in(overridesSettings).value.contains("-Yrangepos"))

  assert(scalacOptions.in(scala211).value.count(_ == "-Yrangepos") == 1)
  assert(scalacOptions.in(scala212).value.count(_ == "-Yrangepos") == 1)
  assert(scalacOptions.in(scala213).value.count(_ == "-Yrangepos") == 1)
}

// https://github.com/sbt/sbt/commit/dbb47b3ce822ff7ec25881dadd71a3b29e202273
// must be outside a macro to workaround "Illegal dynamic reference: Def"
def streamScopedKey(scope: Scope) = Def.ScopedKey(scope, Keys.streams.key)
