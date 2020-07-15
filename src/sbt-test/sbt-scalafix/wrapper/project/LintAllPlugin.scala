import sbt._
import scalafix.sbt.ScalafixPlugin
import scalafix.sbt.ScalafixPlugin.autoImport._

object LintAllPlugin extends AutoPlugin {

  override def trigger: PluginTrigger = allRequirements

  override def requires: Plugins = ScalafixPlugin

  object autoImport {
    val lintAll = taskKey[Unit]("run all linters")
  }

  import autoImport._

  override def projectSettings: Seq[Def.Setting[_]] =
    Seq(
      lintAll := {
        scalafix.in(Compile).toTask(" --check").value
        // & other linters...
      }
    )
}
