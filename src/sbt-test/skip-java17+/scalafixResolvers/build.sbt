scalaVersion := "2.12.21"

import _root_.scalafix.internal.sbt.Compat._
TaskKey[Unit]("check") := Def.uncached {
  val expectedRepositories: Seq[String] = Seq(
    // Repository.central()
    "https://repo1.maven.org/maven2",
    // Repository.ivy2Local()
    ".ivy2/local/",
    "https://central.sonatype.com/repository/maven-snapshots/",
    // custom repository
    "https://a.b.com/artifactory/snapshots"
  )

  // scalafixResolvers is lazily evaluated as a stream of coursier.internal.api.ApiHelper.ApiRepo's. To fully evaluate
  // the stream and overcome object equality issues, build a string from scalafixResolvers.
  var scalafixRepos: String = ""
  (ThisBuild / scalafixResolvers).value.foreach { repo =>
    scalafixRepos += repo.toString
  }

  expectedRepositories.foreach { repository =>
    if (!scalafixRepos.contains(repository)) {
      throw new Exception(
        s"repository=$repository not included in scalafixResolvers"
      )
    }
  }
}
