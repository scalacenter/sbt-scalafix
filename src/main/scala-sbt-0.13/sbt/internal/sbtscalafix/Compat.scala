package sbt.internal.sbtscalafix

import sbt.{Extracted, Setting, State}

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
}
