import sbt._

object Dependencies {
  val x = List(1) // scalafix:ok

  // keep this as low as possible, to allow bumping sbt-scalafix without scalafix-interfaces
  def scalafixInterfacesVersion: String =
    "0.14.2+48-9b6e03ac-SNAPSHOT" // scala-steward:off

  val all = List(
    "ch.epfl.scala" % "scalafix-interfaces" % scalafixInterfacesVersion
      exclude ("ch.epfl.scala", "scalafix-properties"),
    "ch.epfl.scala" % "scalafix-interfaces" % scalafixInterfacesVersion % Test,
    "org.eclipse.jgit" % "org.eclipse.jgit" % "5.13.3.202401111512-r",
    "io.get-coursier" % "interface" % "1.0.28",
    "org.scala-lang.modules" %% "scala-collection-compat" % "2.13.0"
  )
}
