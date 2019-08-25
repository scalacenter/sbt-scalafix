package sbt.internal.sbtscalafix

import sbt.{Extracted, Setting, State}

object Compat {
  type ConsoleLogger = sbt.ConsoleLogger
  val ConsoleLogger = sbt.ConsoleLogger

  def append(
      project: Extracted,
      settings: Seq[Setting[_]],
      state: State
  ): State =
    project.append(settings, state)
}
