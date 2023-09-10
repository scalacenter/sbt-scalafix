inThisBuild(
  List(
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    scalaVersion := "2.13.7" // last semanticdb-scalac published for that scala version is 4.8.4
  )
)

lazy val checkLogs =
  taskKey[Unit]("Check presence of call to action in update logs")

checkLogs := {
  val taskStreams = (update / streams).value
  val reader = taskStreams.readText(taskStreams.key)
  val logLines = Stream
    .continually(reader.readLine())
    .takeWhile(_ != null)
    .force
  assert(
    logLines.exists(_.contains("Please upgrade to a more recent Scala patch version or uninstall sbt-scalafix"))
  )
}
