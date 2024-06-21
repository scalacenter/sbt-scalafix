resolvers += Resolver.sonatypeRepo("public")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % sys.props("plugin.version"))
addSbtPlugin("au.com.onegeek" %% "sbt-dotenv" % "2.1.146")
