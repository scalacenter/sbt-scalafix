resolvers += Resolver.sonatypeCentralSnapshots
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.11.1")

// dogfooding
Compile / unmanagedSourceDirectories ++= {
  val root = (ThisBuild / baseDirectory).value.getParentFile / "src" / "main"
  List(
    root / "scala",
    root / "scala-2.12"
  )
}
libraryDependencies ++= Dependencies.all
