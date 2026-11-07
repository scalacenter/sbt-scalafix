import _root_.scalafix.sbt.{BuildInfo => Versions}

val ruleVersion = "0.0.1-SNAPSHOT"

inThisBuild(
  List(
    scalaVersion := Versions.scala213,
    scalafixDependencies += "test.scalafix" %% "test-rule" % ruleVersion,
    // Exercise pathToUriString with a URL-based Ivy resolver, which would
    // fail on Windows without the fix for
    // https://github.com/scalacenter/scalafix/issues/2363
    resolvers += Resolver.url(
      "ivy-https",
      new java.net.URI("https://example.com/repo/").toURL
    )(Resolver.ivyStylePatterns)
  )
)

// Force resolver conversion to visit the RawRepository compatibility branch.
// Use a real resolver on sbt versions that ship Ivy so their own resolver engine
// can also handle it; sbt 2.1 falls back to an opaque object and exposes a missing
// Ivy class link in the plugin.
val rawResolver: AnyRef = try {
  val resolverClass = Class.forName(
    "org.apache.ivy.plugins.resolver.IBiblioResolver"
  )
  val resolver =
    resolverClass.getConstructor().newInstance().asInstanceOf[AnyRef]
  resolverClass
    .getMethod("setName", classOf[String])
    .invoke(resolver, "link-check")
  resolver
} catch {
  case _: ClassNotFoundException => new Object
}
ThisBuild / resolvers +=
  new sbt.librarymanagement.RawRepository(rawResolver, "link-check")

lazy val rules = project
  .disablePlugins(ScalafixPlugin)
  .settings(
    libraryDependencies += "ch.epfl.scala" %% "scalafix-core" % Versions.scalafixVersion,
    organization := "test.scalafix",
    name := "test-rule",
    version := ruleVersion,
    publishTo := Some(
      Resolver.file("custom-ivy", target.value / "custom-ivy")(
        Resolver.ivyStylePatterns
      )
    ),
    publishMavenStyle := false
  )

lazy val example = project
