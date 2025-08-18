inThisBuild(
  List(
    scalaVersion := "2.12.20",
    libraryDependencies ++= List(
      "org.scalameta" %% "testkit" % "4.3.10" % Test,
      "org.scalameta" %% "munit" % "0.7.9" % Test,
      "org.scalacheck" %% "scalacheck" % "1.13.5" % Test,
      "org.scalatest" %% "scalatest" % "3.2.19" % Test
    ),
    scalafixDependencies := List(
      // Custom rule published to Maven Central https://github.com/scalacenter/example-scalafix-rule
      "ch.epfl.scala" %% "example-scalafix-rule" % "1.4.0"
    )
  )
)

lazy val example = project
  .settings(
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    scalacOptions += "-Ywarn-unused-import"
  )

lazy val tests = project

lazy val checkLogs = taskKey[Unit]("Check that diffs are logged as errors")

checkLogs := {
  val taskStreams = (example / Compile / scalafix / streams).value
  val reader = taskStreams.readText(taskStreams.key)
  val logLines = Stream
    .continually(reader.readLine())
    .takeWhile(_ != null)
    .map(_.replaceAll("\u001B\\[[;\\d]*m", "")) // remove control chars (colors)
    .force
  assert(
    logLines.exists(_ == "[error] -import scala.concurrent.Future"),
    "diff should be logged as error"
  )
}
