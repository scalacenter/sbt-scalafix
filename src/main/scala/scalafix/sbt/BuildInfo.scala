package scalafix.sbt

import java.util.Properties

import sbt.internal.sbtscalafix.Compat

object BuildInfo {
  private[this] val Logger = Compat.ConsoleLogger(System.out)

  def scalafixVersion: String =
    property("scalafixVersion", "0.9.3")
  @deprecated("Use scalafixVersion", "0.9.7")
  def scalafixStableVersion: String =
    property("scalafixStableVersion", "0.9.3")
  def scalametaVersion: String =
    property("scalametaVersion", "4.1.0")
  def scala212: String =
    property("scala212", "2.12.7")
  def scala211: String =
    property("scala211", "2.11.12")
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
        Logger.error(s"failed to load $path")
    }
    props
  }

  private def property(key: String, default: String): String =
    Option(props.getProperty(key)).getOrElse {
      Logger.warn(
        s"property $key missing; falling back to older version $default"
      )
      default
    }
}
