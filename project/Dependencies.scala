import sbt._

object Dependencies {
  val x = List(1) // scalafix:ok
  def scalafixVersion: String = "0.14.3"

  val all = List(
    "org.eclipse.jgit" % "org.eclipse.jgit" % "5.13.3.202401111512-r",
    "ch.epfl.scala" % "scalafix-interfaces" % scalafixVersion,
    "io.get-coursier" % "interface" % "1.0.28",
    "org.scala-lang.modules" %% "scala-collection-compat" % "2.13.0"
  )
}
