import sbt._

object Dependencies {
  val x = List(1) // scalafix:ok
  def scalafixVersion: String = "0.9.30"

  val all = List(
    "org.eclipse.jgit" % "org.eclipse.jgit" % "5.12.0.202106070339-r",
    "ch.epfl.scala" % "scalafix-interfaces" % scalafixVersion,
    // coursier-small provides a binary stable API around Coursier making sure that
    // sbt-scalafix doesn't conflict with the user's installed version of sbt-coursier.
    // Details: https://github.com/olafurpg/coursier-small
    "com.geirsson" %% "coursier-small" % "1.3.3",
    "io.get-coursier" % "interface" % "1.0.4"
  )
}
