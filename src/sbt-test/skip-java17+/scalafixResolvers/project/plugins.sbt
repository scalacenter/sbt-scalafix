resolvers += Resolver.sonatypeRepo("public")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % sys.props("plugin.version"))
addSbtPlugin("nl.gn0s1s" %% "sbt-dotenv" % "3.1.1")
