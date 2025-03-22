ThisBuild / scalafixDependencies := Nil
ThisBuild / scalafixResolvers := Nil
scalafixCaching := false
scalafixConfig := None
scalafixOnCompile := false

lazy val checkLintWarns = taskKey[Unit]("")
checkLintWarns := {
  // https://github.com/sbt/sbt/blob/v1.4.9/sbt/src/sbt-test/project/lint/build.sbt
  val state = Keys.state.value
  val includeKeys =
    (Global / includeLintKeys).value.map(_.scopedKey.key.label)
  val excludeKeys =
    // for some reason watchPersistFileStamps comes up as unused in sbt 2.0.0-M4
    // when LintUnused.lintUnused is invoked (but not at startup) ...
    ((Global / excludeLintKeys).value + sbt.nio.Keys.watchPersistFileStamps)
      .map(_.scopedKey.key.label)
  val result =
    sbt.internal.LintUnused.lintUnused(state, includeKeys, excludeKeys)
  assert(result.size == 0)
}
