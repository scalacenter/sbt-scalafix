ThisBuild / scalafixDependencies := List(
  "ch.epfl.scala" %% "example-scalafix-rule" % "6.0.0+19-2945f6b4-SNAPSHOT"
)

import scala.collection.JavaConverters.collectionAsScalaIterableConverter

ThisBuild / scalafixResolvers := coursierapi.Repository.defaults().asScala.toSeq
