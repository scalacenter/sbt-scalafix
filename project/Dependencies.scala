import sbt._

object Dependencies {
  val x = List(1) // scalafix:ok
  def scalafixVersion: String = "0.10.4+78-f6b8c84d-SNAPSHOT"

  val all = List(
    "org.eclipse.jgit" % "org.eclipse.jgit" % "5.13.1.202206130422-r",
    "ch.epfl.scala" % "scalafix-interfaces" % scalafixVersion,
    "io.get-coursier" % "interface" % "1.0.14"
  )
}
