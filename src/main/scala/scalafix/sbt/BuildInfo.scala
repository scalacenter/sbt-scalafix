package scalafix.sbt

import java.util.Properties

object BuildInfo {

  def scalafixVersion: String =
    props.getProperty("scalafixVersion", "0.9.0")
  def scalafixStableVersion: String =
    props.getProperty("scalafixStableVersion", "0.9.0")
  def scalametaVersion: String =
    props.getProperty("scalametaVersion", "4.1.0")
  def scala212: String =
    props.getProperty("scala212", "2.12.7")
  def scala211: String =
    props.getProperty("scala211", "2.11.12")
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
