package scalafix.internal.sbt

import java.nio.file.Path

import sbt.ModuleID

import scala.collection.mutable.ListBuffer

class SemanticRuleValidator(ifNotFound: SemanticdbNotFound) {
  def findErrors(
      files: Seq[Path],
      dependencies: Seq[ModuleID],
      scalacOpts: Seq[String],
      interface: ScalafixInterface
  ): Seq[String] = {
    if (files.isEmpty) Nil
    else {
      val errors = ListBuffer.empty[String]
      val hasSemanticdb =
        dependencies.exists(_.name.startsWith("semanticdb-scalac")) ||
          scalacOpts.contains("-Xsemanticdb")
      if (!hasSemanticdb)
        errors += ifNotFound.message
      val invalidArguments = interface.validate()
      invalidArguments.foreach { invalidArgument =>
        errors += invalidArgument.getMessage
      }
      errors
    }
  }
}

class SemanticdbNotFound(
    ruleNames: Seq[String],
    scalaVersion: String
) {
  def message: String = {
    val names = ruleNames.mkString(", ")

    val recommendedSetting =
      if (SemanticdbPlugin.available) semanticdbPluginSetup(scalaVersion)
      else manualSetup

    s"""|The scalac compiler should produce semanticdb files to run semantic rules like $names.
      |To fix this problem for this sbt shell session, run `scalafixEnable` and try again.
      |To fix this problem permanently for your build, add the following settings to build.sbt:
      |
      |$recommendedSetting
      |""".stripMargin
  }

  private def semanticdbPluginSetup(scalaVersion: String) =
    s"""inThisBuild(
      |  List(
      |    scalaVersion := "$scalaVersion",
      |    semanticdbEnabled := true,
      |    semanticdbVersion := scalafixSemanticdb.revision
      |  )
      |)
      |""".stripMargin

  private val manualSetup =
    s"""addCompilerPlugin(scalafixSemanticdb)
      |scalacOptions += "-Yrangepos"
      |""".stripMargin
}
