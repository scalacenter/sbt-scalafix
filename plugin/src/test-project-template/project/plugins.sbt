libraryDependencies += "com.googlecode.java-diff-utils" % "diffutils" % "1.3.0"

resolvers += Resolver.sonatypeRepo("releases")

addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % sys.props("plugin.version"))
