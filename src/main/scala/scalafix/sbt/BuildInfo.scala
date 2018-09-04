package scalafix.sbt

import java.util.Properties

object BuildInfo {

  def scalafixVersion: String =
    props.getProperty("scalafixVersion", "0.6.0-M17")
  def scalafixStableVersion: String =
    props.getProperty("scalafixStableVersion", "0.5.10")
  def scalametaVersion: String =
    props.getProperty("scalametaVersion", "4.0.0-M10")
  def scala212: String =
    props.getProperty("scala212", "2.12.6")
  def scala211: String =
    props.getProperty("scala211", "2.11.11")
  def supportedScalaVersions: List[String] =
    List(scala212, scala211)

  private lazy val props: Properties = {
    val props = new Properties()
    val path = "scalafix-interfaces.properties"
    val classloader = this.getClass.getClassLoader
    Option(classloader.getResourceAsStream(path)) match {
      case Some(stream) =>
        props.load(stream)
      case None =>
        println(s"error: failed to load $path")
    }
    props
  }
}
