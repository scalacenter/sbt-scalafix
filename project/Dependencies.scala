import sbt._

object Dependencies {
  val x = List(1) // scalafix:ok
  def scalafixVersion: String = "0.14.9"

  val all = List(
    "org.eclipse.jgit" % "org.eclipse.jgit" % "5.13.5.202508271544-r",
    "ch.epfl.scala" % "scalafix-interfaces" % scalafixVersion,
    "io.get-coursier" % "interface" % "1.0.30",
    "org.scala-lang.modules" %% "scala-collection-compat" % "2.14.0"
  )
}
