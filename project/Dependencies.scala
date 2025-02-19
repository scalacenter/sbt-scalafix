import sbt._

object Dependencies {
  val x = List(1) // scalafix:ok
  def scalafixVersion: String = "0.14.0+51-953bdc64-SNAPSHOT"

  val all = List(
    "org.eclipse.jgit" % "org.eclipse.jgit" % "5.13.3.202401111512-r",
    "ch.epfl.scala" % "scalafix-interfaces" % scalafixVersion,
    "io.get-coursier" % "interface" % "1.0.28"
  )
}
