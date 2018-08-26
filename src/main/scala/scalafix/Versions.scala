package scalafix

import scalafix.sbt.BuildInfo

@deprecated("Use scalafix.sbt.BuildInfo instead", "0.6.0")
object Versions {
  def version = BuildInfo.version
  def scala212: String = BuildInfo.scala212
  def scala211: String = BuildInfo.scala211
  def scalameta: String = BuildInfo.scalameta
  def scalafix: String = BuildInfo.scalafix
  def supportedScalaVersions: List[String] = BuildInfo.supportedScalaVersions
}
