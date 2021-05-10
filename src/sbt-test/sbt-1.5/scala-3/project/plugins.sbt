resolvers += Resolver.sonatypeRepo("public")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % sys.props("plugin.version"))

//FIXME: remove when scalafixVersion is bumped to 0.9.28
resolvers += Resolver.sonatypeRepo("snapshots")
dependencyOverrides += "ch.epfl.scala" % "scalafix-interfaces" % "0.9.27+52-6c9eeec9-SNAPSHOT"
