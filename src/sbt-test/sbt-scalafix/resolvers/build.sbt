ThisBuild / scalafixDependencies := List(
  "ch.epfl.scala" %% "example-scalafix-rule" % "4.0.0+3-e9850db5-SNAPSHOT"
)

import scala.collection.JavaConverters.collectionAsScalaIterableConverter

ThisBuild / scalafixResolvers := coursierapi.Repository.defaults().asScala.toSeq
