import _root_.scalafix.sbt.{BuildInfo => V}

import scala.collection.mutable

inThisBuild(
  List(
    addCompilerPlugin(scalafixSemanticdb),
    scalacOptions += "-Yrangepos",
    scalacOptions += "-Ywarn-unused", // for RemoveUnused
    scalaVersion := V.scala213,
    postScalafixHooks += { files: Seq[File] =>
      lastCallbacks.push(files)
      ()
    },
    commands ++= Seq(checkLastCallbackContains, checkNoCallback)
  )
)

// redeclare this here to make sure it can be used without a build-time dependency
val postScalafixHooks = settingKey[Seq[Seq[File] => Unit]]("")

val lastCallbacks = new mutable.Stack[Seq[File]]

val checkLastCallbackContains: Command =
  Command.args("checkLastCallbackContains", "<expectedName>") {
    (state: State, expectedNames: Seq[String]) =>
      val lastCallbackFileNames = lastCallbacks.pop.map(_.getName)
      assert(
        expectedNames.forall { expectedName =>
          lastCallbackFileNames.contains(expectedName)
        },
        s"missing files in callback: expected $expectedNames, actual $lastCallbackFileNames"
      )
      state
  }

val checkNoCallback: Command = Command.command("checkNoCallback") {
  state: State =>
    assert(lastCallbacks.isEmpty, "unexpected callback")
    state
}
