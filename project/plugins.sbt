resolvers += Resolver.sonatypeRepo("releases")
addSbtPlugin("com.geirsson" % "sbt-ci-release" % "1.2.1")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.9.0")
addSbtPlugin(
  "io.get-coursier" % "sbt-coursier" % coursier.util.Properties.version
)

libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value
