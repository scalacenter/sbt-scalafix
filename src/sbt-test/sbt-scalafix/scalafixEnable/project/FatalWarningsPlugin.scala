import sbt.Keys._
import sbt._
import scalafix.sbt.ScalafixPlugin

object FatalWarningsPlugin extends AutoPlugin {

  override def trigger: PluginTrigger = allRequirements

  // This is not a real use-case, but by forcing the order of evaluation, we can make sure scalacOptions
  // will be relaxed even though they are set by a plugin (such as https://github.com/DavidGregory084/sbt-tpolecat)
  // and that plugin happens to run AFTER ScalafixPlugin
  override def requires: Plugins = ScalafixPlugin

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    Test / compile / scalacOptions := {
      val old = (Test / compile / scalacOptions).value
      val strict = Seq(
        "-Xfatal-warnings",
        "-Ywarn-unused"
      )
      // For sbt 1.3+ where SemanticdbPlugin is available and preferred to manual tweaking,
      // scalafixEnable does not manage to enable Semanticdb when scalacOptions are overriden
      if (sbtVersion.value.split("\\.")(1).toInt >= 3) old ++ strict else strict
    }
  )
}
