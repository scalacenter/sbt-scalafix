resolvers += Resolver.sonatypeRepo("releases")
addSbtPlugin("com.geirsson" % "sbt-ci-release" % "1.2.1")
addSbtPlugin(
  "io.get-coursier" % "sbt-coursier" % coursier.util.Properties.version
)

libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value
