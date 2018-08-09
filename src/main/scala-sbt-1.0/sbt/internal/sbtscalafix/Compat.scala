package sbt.internal.sbtscalafix

object Compat {
  type ConsoleLogger = sbt.internal.util.ConsoleLogger
  val ConsoleLogger = sbt.internal.util.ConsoleLogger
}
