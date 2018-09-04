package scalafix

import scalafix.sbt.BuildInfo

@deprecated("Use scalafix.sbt.BuildInfo instead", "0.6.0")
object Versions {
  def version: String = BuildInfo.scalafixVersion
  def scala212: String = BuildInfo.scala212
  def scala211: String = BuildInfo.scala211
  def scalameta: String = BuildInfo.scalametaVersion
  def scalafix: String = BuildInfo.scalafixVersion
  def supportedScalaVersions: List[String] = BuildInfo.supportedScalaVersions
}
