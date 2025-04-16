lazy val checkLogs =
  taskKey[Unit]("Check presence of call to action in last logs")

checkLogs := {
  val lastLog: File = BuiltinCommands.lastLogFile(state.value).get
  val last: String = IO.read(lastLog)
  assert(
    last.contains(
      "add \"ch.epfl.scala\" % \"scalafix-interfaces\" to libraryDependencies in 'project/plugins.sbt'"
    ),
    "actionable error should be logged"
  )
}
