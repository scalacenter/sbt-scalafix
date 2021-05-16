resolvers += Resolver.sonatypeRepo("public")
addSbtPlugin("com.geirsson" % "sbt-ci-release" % "1.5.7")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.10.0")

libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value
unmanagedSourceDirectories.in(Compile) ++= {
  val root = baseDirectory.in(ThisBuild).value.getParentFile / "src" / "main"
  List(
    root / "scala",
    root / "scala-sbt-1.0"
  )
}
libraryDependencies ++= Dependencies.all
libraryDependencies ++= Dependencies.sbt1Plugins.flatMap { plugin =>
  val sbtV = (sbtBinaryVersion in pluginCrossBuild).value
  val scalaV = (scalaBinaryVersion in update).value
  if (sbtV.startsWith("0.13")) Nil
  else Seq(sbt.Defaults.sbtPluginExtra(plugin, sbtV, scalaV))
}
