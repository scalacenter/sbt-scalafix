package sbt.internal.sbtscalafix

import sbt.{Compiler, Extracted, Setting, State}
import sbt.inc.IncOptions

object Compat {
  type ConsoleLogger = sbt.ConsoleLogger
  val ConsoleLogger = sbt.ConsoleLogger

  val Disabled = sbt.CrossVersion.Disabled

  def append(
      project: Extracted,
      settings: Seq[Setting[_]],
      state: State
  ): State =
    project.append(settings, state)

  def addIgnoredScalacOptions(
      incOptions: IncOptions,
      ignoredScalacOptions: Seq[String]
  ): IncOptions = {
    // ignoredScalacOptions was added in 1.2 https://github.com/sbt/zinc/commit/868182936686679b3e0c7569f69d843323b4d712
    incOptions
  }

  def withHasModified(
      compileResult: Compiler.CompileResult,
      hasModified: Boolean
  ): Compiler.CompileResult = {
    compileResult
  }
}
