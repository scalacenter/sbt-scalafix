package scalafix.sbt


object BuildInfo {
  // TODO(olafur): read these from scalafix-interfaces https://github.com/scalacenter/scalafix/issues/788
  def version: String = "0.6.0-M17"
  def scala212: String = "2.12.6"
  def scala211: String = "2.11.12"
  def scalameta: String = "4.0.0-M11"
  def scalafix: String = "0.6.0-M17"
  def supportedScalaVersions: List[String] = List(scala212, scala211)
}
