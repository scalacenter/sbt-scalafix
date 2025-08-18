resolvers += Resolver.sonatypeRepo("public") // for old snapshot
resolvers += sonatypeCentralRepo("maven-snapshots")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % sys.props("plugin.version"))
