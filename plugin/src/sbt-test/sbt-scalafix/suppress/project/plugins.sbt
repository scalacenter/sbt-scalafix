resolvers += sonatypeCentralRepo("maven-snapshots")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % sys.props("plugin.version"))
libraryDependencies += "com.googlecode.java-diff-utils" % "diffutils" % "1.3.0"
