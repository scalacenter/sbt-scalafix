resolvers += Resolver.sonatypeRepo("public")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % sys.props("plugin.version"))
libraryDependencies += "ch.epfl.scala" % "scalafix-interfaces" %
  sys.props("scalafix-interfaces.version")
libraryDependencies += "com.googlecode.java-diff-utils" % "diffutils" % "1.3.0"
