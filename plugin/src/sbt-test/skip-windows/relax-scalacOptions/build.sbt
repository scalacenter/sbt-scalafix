val V = _root_.scalafix.sbt.BuildInfo

semanticdbEnabled := true
semanticdbVersion := scalafixSemanticdb.revision
scalacOptions += "-Ywarn-unused"
scalaVersion := V.scala212
Compile / compile / scalacOptions ++= Seq(
  // generate errors on procedure syntax
  "-Wconf:cat=deprecation:e",
  // suppress the warning that is coming from IgnoreWarning
  "-Wconf:src=scala/IgnoreWarning.scala:s",
  "-Xfuture",
  "-deprecation"
)
TaskKey[Unit]("checkLastCompilationCached") := {
  val str = streams.value
  val thisProject = thisProjectRef.value

  // readText expects a fully resolved scope (no This)
  val compileIncScope = Scope(
    Select(thisProject),
    Select(Compile),
    Select(compileIncremental.key),
    Zero
  )
  val reader = str.readText(streamScopedKey(compileIncScope), None)
  val lines = IO.readLines(reader)

  // in sbt 2.x, zinc may not even be called thanks to the remote cache, so we clear logs
  // to silence false positives via `lines.length > 0` in the next invocation
  IO.delete((Compile / compileIncremental / streams).value.cacheDirectory)

  val cachedCompilationLogEntry = "No changes"
  if (
    lines.length > 0 &&
    lines.forall(line => !line.contains(cachedCompilationLogEntry))
  ) {
    lines.foreach(line => str.log.error(line))
    throw new RuntimeException("last compilation was not fully cached")
  }
  lines.foreach(line => str.log.info(line))
}

// https://github.com/sbt/sbt/commit/dbb47b3ce822ff7ec25881dadd71a3b29e202273
// must be outside a macro to workaround "Illegal dynamic reference: Def"
def streamScopedKey(scope: Scope) = Def.ScopedKey(scope, Keys.streams.key)
