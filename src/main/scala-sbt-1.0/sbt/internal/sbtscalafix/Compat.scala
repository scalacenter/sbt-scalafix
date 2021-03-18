package sbt.internal.sbtscalafix

import sbt.internal.inc.ScalaInstance
import sbt.{Classpaths, Extracted, IncOptions, Setting, State, UpdateReport}
import xsbti.compile.CompileResult

object Compat {
  type ConsoleLogger = sbt.internal.util.ConsoleLogger
  val ConsoleLogger = sbt.internal.util.ConsoleLogger

  // https://github.com/sbt/sbt/issues/4977
  val Disabled = sbt.librarymanagement.Disabled()

  def append(
      project: Extracted,
      settings: Seq[Setting[_]],
      state: State
  ): State =
    project.appendWithoutSession(settings, state)

  def addIgnoredScalacOptions(
      incOptions: IncOptions,
      ignoredScalacOptions: Seq[String]
  ): IncOptions = {
    incOptions.withIgnoredScalacOptions(
      incOptions.ignoredScalacOptions() ++ ignoredScalacOptions
    )
  }

  def withHasModified(
      compileResult: CompileResult,
      hasModified: Boolean
  ): CompileResult = {
    compileResult.withHasModified(hasModified)
  }

  def autoPlugins(
      report: UpdateReport,
      scalaVersion: String
  ): Seq[String] = {
    Classpaths.autoPlugins(
      report,
      Seq(),
      ScalaInstance.isDotty(scalaVersion)
    )
  }
}
