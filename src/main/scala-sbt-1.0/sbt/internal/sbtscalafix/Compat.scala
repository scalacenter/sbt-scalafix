package sbt.internal.sbtscalafix

import sbt.{Extracted, Setting, State}

object Compat {
  type ConsoleLogger = sbt.internal.util.ConsoleLogger
  val ConsoleLogger = sbt.internal.util.ConsoleLogger

  def append(
      project: Extracted,
      settings: Seq[Setting[_]],
      state: State
  ): State =
    project.appendWithoutSession(settings, state)
}
