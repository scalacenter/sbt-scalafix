package scalafix.sbt

import java.util.Properties

object BuildInfo {
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
  @deprecated("Scala 2.11 is no longer supported", ">0.10.4")
  def scala211: String = ???
  def supportedScalaVersions: List[String] =
    List(scala213, scala212)

  private val propertiesPath = "scalafix-interfaces.properties"

  private lazy val props: Properties = {
    val props = new Properties()
    val classloader = this.getClass.getClassLoader
    Option(classloader.getResourceAsStream(propertiesPath)) match {
      case Some(stream) =>
        props.load(stream)
      case None =>
        sys.error(
          s"failed to load the resource file '$propertiesPath'; " +
            "to fix this problem, add \"ch.epfl.scala\" % \"scalafix-interfaces\" " +
            "to libraryDependencies in 'project/plugins.sbt'"
        )
    }
    props
  }

  private def property(key: String): String =
    Option(props.getProperty(key)).getOrElse {
      sys.error(
        s"sbt-scalafix property '$key' missing in $propertiesPath; " +
          "to fix this problem, upgrade to a more recent version of " +
          "\"ch.epfl.scala\" % \"scalafix-interfaces\""
      )
    }
}
