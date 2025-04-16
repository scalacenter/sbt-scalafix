ThisBuild / scalafixDependencies += "ch.epfl.scala" %% "example-scalafix-rule" % "1.6.0"

lazy val scala210 = project
  .in(file("scala210"))
  .settings(
    scalaVersion := "2.10.7" // unsupported by semanticdb-scalac, unsupported by scalafix-interfaces
  )

lazy val scala211 = project
  .in(file("scala211"))
  .settings(
    scalaVersion := "2.11.12" // supported by old semanticdb-scalac, no longer supported by scalafix-interfaces
  )

lazy val scala212 = project
  .in(file("scala212"))
  .settings(
    scalaVersion := "2.12.20" // supported by semanticdb-scalac, supported by scalafix-interfaces
  )

lazy val scala3 = project
  .in(file("scala3"))
  .settings(
    scalaVersion := "3.3.5" // built-in support for semanticdb, supported by scalafix-interfaces
  )
