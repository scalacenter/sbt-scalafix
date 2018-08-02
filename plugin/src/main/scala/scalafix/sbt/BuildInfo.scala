package scalafix.sbt

object BuildInfo {
  def scala212: String = "2.12.6"
  def scala211: String = "2.11.12"
  def scalameta: String = "4.0.0-M6"
  def scalafix: String = "0.6.0-M12"
  def supportedScalaVersions: List[String] = List("2.12.6", "2.11.12")
}
