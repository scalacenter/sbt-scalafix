resolvers += MavenRepository(
  "sonatype-central-maven-snapshots",
  "https://central.sonatype.com/repository/maven-snapshots/"
)
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % sys.props("plugin.version"))
addSbtPlugin(
  "nl.gn0s1s" %%
    "sbt-dotenv" % {
      // recent versions (those cross-published for sbt 2.x) are published with the new maven pattern only,
      // supported on sbt 1.9+ (see https://github.com/sbt/sbt/pull/7096)
      val sbtVersion = System.getProperty("sbt.version")
      if (sbtVersion.startsWith("1.")) "3.1.1"
      else "3.2.0"
    }
)
