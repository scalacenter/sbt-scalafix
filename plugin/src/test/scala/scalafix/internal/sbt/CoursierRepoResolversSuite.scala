package scalafix.internal.sbt

import org.scalatest.funsuite.AnyFunSuite

class CoursierRepoResolversSuite extends AnyFunSuite {

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
}
