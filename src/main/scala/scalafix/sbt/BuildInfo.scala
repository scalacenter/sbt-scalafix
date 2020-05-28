package scalafix.sbt

import java.util.Properties

import sbt.internal.sbtscalafix.Compat

object BuildInfo {
  private[this] val Logger = Compat.ConsoleLogger(System.out)

  def scalafixVersion: String =
    property("scalafixVersion")
  @deprecated("Use scalafixVersion", "0.9.7")
  def scalafixStableVersion: String =
    property("scalafixStableVersion")
  def scalametaVersion: String =
    property("scalametaVersion")
  def scala213: String =
    property("scala213")
  def scala212: String =
    property("scala212")
  def scala211: String =
    property("scala211")
  def supportedScalaVersions: List[String] =
    List(scala213, scala212, scala211)

  private val propertiesPath = "scalafix-interfaces.properties"

  private lazy val props: Properties = {
    val props = new Properties()
    val classloader = this.getClass.getClassLoader
    Option(classloader.getResourceAsStream(propertiesPath)) match {
      case Some(stream) =>
        props.load(stream)
      case None =>
        sys.error(
          s"failed to load $propertiesPath; is scalafix-interfaces in the plugins classpath?"
        )
    }
    props
  }

  private def property(key: String): String =
    Option(props.getProperty(key)).getOrElse {
      sys.error(
        s"sbt-scalafix property '$key' missing in $propertiesPath; " +
          "try to bring a more recent scalafix-interfaces into the plugins classpath?"
      )
    }
}
