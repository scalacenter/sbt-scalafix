package scalafix.internal.sbt

import sbt.librarymanagement.FileConfiguration
import sbt.librarymanagement.FileRepository
import sbt.librarymanagement.Patterns
import sbt.librarymanagement.Resolver
import sbt.util.Logger

import coursierapi.IvyRepository
import org.scalatest.funsuite.AnyFunSuite

class CoursierRepoResolversSuite extends AnyFunSuite {

  private val ivyBasePattern =
    "[organisation]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)([branch]/)[revision]/[type]s/[artifact](-[classifier]).[ext]"

  // Avoids triggering -Xlint:missing-interpolator on the literal.
  private val ivyHomeProperty = "$" + "{ivy.home}"

  private def fileResolver(pattern: String): Resolver =
    FileRepository(
      "custom",
      FileConfiguration(true, None),
      Patterns(
        ivyPatterns = Vector(pattern),
        artifactPatterns = Vector(pattern),
        isMavenCompatible = false,
        descriptorOptional = false,
        skipConsistencyCheck = false
      )
    )

  private def asRepository(resolver: Resolver): Option[IvyRepository] =
    CoursierRepoResolvers
      .repository(
        resolver,
        Logger.Null,
        Map.empty,
        allowIvyRawRepositories = true
      )
      .collect { case repo: IvyRepository => repo }

  test("pathToUriString returns https URL patterns as-is") {
    val pattern =
      "https://a.b.com/artifactory/snapshots/[organisation]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]"
    assert(CoursierRepoResolvers.pathToUriString(pattern) == pattern)
  }

  test("pathToUriString returns http URL patterns as-is") {
    val pattern =
      "http://repo.example.com/ivy/[organisation]/[module]/[revision]/[artifact](-[classifier]).[ext]"
    assert(CoursierRepoResolvers.pathToUriString(pattern) == pattern)
  }

  test("pathToUriString returns plain https URL as-is") {
    val url = "https://repo1.maven.org/maven2/"
    assert(CoursierRepoResolvers.pathToUriString(url) == url)
  }

  test("pathToUriString converts local path to file URI") {
    val path = "/tmp/ivy-repo/"
    val result = CoursierRepoResolvers.pathToUriString(path)
    assert(result.startsWith("file:"))
    assert(result.contains("ivy-repo"))
  }

  test("pathToUriString handles local path with whitespace") {
    val path = "/tmp/my repo/ivy/"
    val result = CoursierRepoResolvers.pathToUriString(path)
    assert(result.startsWith("file:"))
    assert(result.contains("my%20repo"))
  }

  test("pathToUriString converts local path with pattern placeholders") {
    val path =
      "/tmp/ivy-repo/[organisation]/[module]/[revision]/[artifact].[ext]"
    val result = CoursierRepoResolvers.pathToUriString(path)
    assert(result.startsWith("file:"))
    assert(
      result.endsWith(
        "[organisation]/[module]/[revision]/[artifact].[ext]"
      )
    )
  }

  test("pathToUriString handles path starting with pattern placeholder") {
    val path = "[organisation]/[module]/[revision]/[artifact].[ext]"
    val result = CoursierRepoResolvers.pathToUriString(path)
    assert(
      result == "file://" + path
    )
  }

  test("pathToUriString keeps the separator before pattern placeholders") {
    val base = new java.io.File(
      System.getProperty("java.io.tmpdir"),
      "sbt-scalafix-missing-" + java.util.UUID.randomUUID()
    )
    assert(!base.exists())
    val result = CoursierRepoResolvers.pathToUriString(
      base.getAbsolutePath + "/[organisation]/[module]"
    )
    assert(result.endsWith("/[organisation]/[module]"))
  }

  test("expandIvyHome expands sbt's ivy.home property") {
    val expanded =
      CoursierRepoResolvers.expandIvyHome(
        s"$ivyHomeProperty/local/[organisation]"
      )
    assert(!expanded.contains("$" + "{"))
    assert(expanded.endsWith("/local/[organisation]"))
  }

  test("repository expands sbt's default local ivy repository") {
    val repo = asRepository(Resolver.defaultLocal)
    assert(repo.isDefined)
    val pattern = repo.get.getPattern
    assert(!pattern.contains("$" + "{"))
    assert(pattern.endsWith(s"/local/$ivyBasePattern"))
  }

  test("repository converts ivy repositories without properties") {
    val repo = asRepository(fileResolver(s"/tmp/custom-ivy/$ivyBasePattern"))
    assert(repo.isDefined)
    assert(repo.get.getPattern.startsWith("file:"))
  }

  test("repository skips ivy repositories with unresolved properties") {
    val unresolvedProperty = "$" + "{unknown.home}"
    val repo =
      asRepository(fileResolver(s"$unresolvedProperty/$ivyBasePattern"))
    assert(repo.isEmpty)
  }
}
