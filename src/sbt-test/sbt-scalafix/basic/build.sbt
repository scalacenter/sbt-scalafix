import com.geirsson.coursiersmall.Repository // deprecated, but checks source compatibility

inThisBuild(
  List(
    scalaVersion := "2.12.11",
    libraryDependencies ++= List(
      "org.scalameta" %% "testkit" % "4.0.0-M6" % Test,
      "org.scalacheck" %% "scalacheck" % "1.13.5" % Test,
      "org.scalatest" %% "scalatest" % "3.1.2" % Test
    ),
    scalafixDependencies := List(
      // Custom rule published to Maven Central https://github.com/olafurpg/example-scalafix-rule
      "com.geirsson" %% "example-scalafix-rule" % "1.3.0"
    ),
    scalafixResolvers := List(Repository.MavenCentral)
  )
)

lazy val example = project
  .settings(
    Defaults.itSettings,
    inConfig(IntegrationTest)(scalafixConfigSettings(IntegrationTest)),
    addCompilerPlugin(scalafixSemanticdb),
    scalacOptions ++= List(
      "-Yrangepos",
      "-Ywarn-unused-import"
    )
  )

lazy val tests = project

lazy val checkLogs = taskKey[Unit]("Check that diffs are logged as errors")

checkLogs := {
  val taskStreams = streams.in(scalafix).in(Compile).in(example).value
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
