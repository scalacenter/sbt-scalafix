inThisBuild(
  List(
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    scalaVersion := "2.13.7" // last semanticdb-scalac published for that scala version is 4.8.4
  )
)

lazy val checkLogs =
  taskKey[Unit]("Check presence of call to action in update logs")

import _root_.scalafix.internal.sbt.Compat._
checkLogs := Def.uncached {
  val taskStreams = (update / streams).value
  val reader = taskStreams.readText(taskStreams.key)
  val logLines = Stream
    .continually(reader.readLine())
    .takeWhile(_ != null)
    .force
  assert(
    logLines.exists(
      _.contains(
        "Please consider upgrading to a more recent version of sbt-scalafix and/or Scala, or uninstalling sbt-scalafix plugin"
      )
    )
  )
}
