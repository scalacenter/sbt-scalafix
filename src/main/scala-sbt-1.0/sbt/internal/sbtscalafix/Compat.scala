package sbt.internal.sbtscalafix

import sbt.{Extracted, IncOptions, Setting, State}

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
}
