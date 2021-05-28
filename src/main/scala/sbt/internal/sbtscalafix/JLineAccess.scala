package sbt.internal.sbtscalafix

/** Helper class to access sbt's JLine instance */
object JLineAccess {
  def terminalWidth: Int = sbt.internal.util.JLine.usingTerminal(_.getWidth)
}
