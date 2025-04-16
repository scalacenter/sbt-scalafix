resolvers += Resolver.sonatypeRepo("public")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % sys.props("plugin.version"))
libraryDependencies += "ch.epfl.scala" % "scalafix-interfaces" %
  sys.props("scalafix-interfaces.version")
