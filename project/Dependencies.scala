import sbt._

object Dependencies {
  val x = List(1) // scalafix:ok
  def scalafixVersion: String = "0.11.0+132-89b843d2-SNAPSHOT"

  val all = List(
    "org.eclipse.jgit" % "org.eclipse.jgit" % "5.13.2.202306221912-r",
    "ch.epfl.scala" % "scalafix-interfaces" % scalafixVersion,
    "io.get-coursier" % "interface" % "1.0.18"
  )
}
