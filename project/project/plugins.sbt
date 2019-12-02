addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.1.0-M14-4")
unmanagedSources.in(Compile) +=
  baseDirectory.in(ThisBuild).value.getParentFile / "Dependencies.scala"
