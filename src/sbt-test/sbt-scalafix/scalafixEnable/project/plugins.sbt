resolvers += Resolver.sonatypeRepo("public")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % sys.props("plugin.version"))

// https://github.com/scalameta/scalameta/blob/v4.4.10/project/Versions.scala
dependencyOverrides += "ch.epfl.scala" % "scalafix-interfaces" % "0.9.27" // scala-steward:off
