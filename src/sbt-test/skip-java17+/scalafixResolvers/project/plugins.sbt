resolvers += MavenRepository(
  "sonatype-central-maven-snapshots",
  "https://central.sonatype.com/repository/maven-snapshots/"
)
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % sys.props("plugin.version"))
addSbtPlugin("nl.gn0s1s" %% "sbt-dotenv" % "3.1.1")
