val V = _root_.scalafix.sbt.BuildInfo

scalaVersion := V.scala212
resolvers += Resolver.sonatypeRepo("snapshots")
libraryDependencies += "ch.epfl.scala" %% "scalafix-core" % V.scalafixVersion % ScalafixConfig

// make it possible to run scalafix in parallel with other tasks via the `all` command
// (work around "Cannot mix input tasks with plain tasks/settings")
val scalafixAddFooter = taskKey[Unit]("task wrapping scalafix")

val addHeader = taskKey[Unit]("")

val check = taskKey[Unit]("")

inConfig(Compile)(
  Seq(
    scalafixAddFooter := scalafix.toTask(" --rules=class:fix.AddFooter").value,
    addHeader := {
      unmanagedSources.value.foreach { file =>
        val content = IO.read(file)
        // artificial delay between read/parsing and write/patching to exercise concurrency
        Thread.sleep(10000)
        IO.write(file, s"// HEADER\n$content")
      }
    },
    check := {
      unmanagedSources.value.foreach { file =>
        val content = IO.read(file)
        assert(content.contains("FOOTER"), s"missing footer in\n<<<$content>>>")
        assert(content.contains("HEADER"), s"missing header in\n<<<$content>>>")
      }
    }
  )
)
