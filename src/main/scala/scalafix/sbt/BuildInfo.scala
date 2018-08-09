package scalafix.sbt

import scalafix.internal.sbtscalafix.{BuildInfo => I}

object BuildInfo {
  // TODO(olafur): read these from scalafix-interfaces https://github.com/scalacenter/scalafix/issues/788
  def scala212: String = I.scala212
  def scala211: String = I.scala211
  def scalameta: String = I.scalametaVersion
  def scalafix: String = I.scalafixVersion
  def supportedScalaVersions: List[String] = List(scala212, scala211)
}
