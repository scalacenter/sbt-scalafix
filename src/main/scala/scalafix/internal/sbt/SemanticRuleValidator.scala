package scalafix.internal.sbt

import java.nio.file.Path

import sbt.{CrossVersion, ModuleID}

import scala.collection.mutable.ListBuffer

class SemanticRuleValidator(ifNotFound: SemanticdbNotFound) {
  def findErrors(
      files: Seq[Path],
      dependencies: Seq[ModuleID],
      interface: ScalafixInterface
  ): Seq[String] = {
    if (files.isEmpty) Nil
    else {
      val errors = ListBuffer.empty[String]
      val hasSemanticdb =
        dependencies.exists(_.name.startsWith("semanticdb-scalac"))
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
    scalaVersion: String,
    sbtVersion: String
) {
  def message: String = {
    val names = ruleNames.mkString(", ")

    val recommendedSetting = CrossVersion.partialVersion(sbtVersion) match {
      case Some((1, n)) if n < 3 => atMostSbt12
      case Some((0, _)) => atMostSbt12
      case _ => atLeastSbt13(scalaVersion)
    }

    s"""|The semanticdb-scalac compiler plugin is required to run semantic rules like $names.
        |To fix this problem for this sbt shell session, run `scalafixEnable` and try again.
        |To fix this problem permanently for your build, add the following settings to build.sbt:
        |
        |$recommendedSetting
        |""".stripMargin
  }

  private def atLeastSbt13(scalaVersion: String) =
    s"""inThisBuild(
       |  List(
       |    scalaVersion := "$scalaVersion",
       |    semanticdbEnabled := true,
       |    semanticdbVersion := scalafixSemanticdb.revision
       |  )
       |)
       |""".stripMargin

  private val atMostSbt12 =
    s"""addCompilerPlugin(scalafixSemanticdb)
       |scalacOptions += "-Yrangepos"
       |""".stripMargin
}
