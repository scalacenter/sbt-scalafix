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
    scalacOptions.in(Test, compile) := Seq(
      "-Xfatal-warnings",
      "-Ywarn-unused"
    )
  )
}
