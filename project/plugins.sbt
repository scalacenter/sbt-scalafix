resolvers ++= Resolver.sonatypeOssRepos("public")
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.11.2")

// dogfooding
Compile / unmanagedSourceDirectories ++= {
  val root = (ThisBuild / baseDirectory).value.getParentFile / "src" / "main"
  List(
    root / "scala",
    root / "scala-3"
  )
}
libraryDependencies ++= Dependencies.all
