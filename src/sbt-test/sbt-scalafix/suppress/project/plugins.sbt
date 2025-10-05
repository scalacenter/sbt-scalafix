resolvers += MavenRepository(
  "sonatype-central-maven-snapshots",
  "https://central.sonatype.com/repository/maven-snapshots/"
)
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % sys.props("plugin.version"))
libraryDependencies += "com.googlecode.java-diff-utils" % "diffutils" % "1.3.0"
