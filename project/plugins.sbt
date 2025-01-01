resolvers ++= Resolver.sonatypeOssRepos("public")
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.9.2")

libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value
Compile / unmanagedSourceDirectories ++= {
  val root = (ThisBuild / baseDirectory).value.getParentFile / "src" / "main"
  List(
    root / "scala",
    root / "scala-sbt-1.0"
  )
}
libraryDependencies ++= Dependencies.all
