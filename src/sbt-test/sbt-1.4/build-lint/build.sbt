ThisBuild / scalafixDependencies := Nil
ThisBuild / scalafixResolvers := Nil
ThisBuild / scalafixScalaBinaryVersion := "2.12"
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
    (Global / excludeLintKeys).value.map(_.scopedKey.key.label)
  val result =
    sbt.internal.LintUnused.lintUnused(state, includeKeys, excludeKeys)
  assert(result.size == 0)
}
