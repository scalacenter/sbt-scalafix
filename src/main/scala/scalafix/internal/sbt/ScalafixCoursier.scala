package scalafix.internal.sbt

import java.io.OutputStreamWriter
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.function
import java.{util => jutil}

import com.geirsson.coursiersmall
import com.geirsson.coursiersmall._
import sbt._
import scalafix.sbt.BuildInfo

import scala.concurrent.duration.Duration

object ScalafixCoursier {
  private def scalafixCli: Dependency = new Dependency(
    "ch.epfl.scala",
    s"scalafix-cli_${BuildInfo.scala212}",
    BuildInfo.scalafixVersion
  )

  def scalafixCliJars: List[Path] = {
    CoursierSmall.fetch(fetchSettings.withDependencies(List(scalafixCli)))
  }
  def scalafixToolClasspath(
      deps: Seq[ModuleID],
      resolvers: Seq[Repository],
      parent: ClassLoader
  ): URLClassLoader = {
    if (deps.isEmpty) {
      new URLClassLoader(Array(), parent)
    } else {
      val jars =
        dependencyCache.computeIfAbsent(
          deps,
          fetchScalafixDependencies(resolvers)
        )
      val urls = jars.map(_.toUri.toURL).toArray
      val classloader = new URLClassLoader(urls, parent)
      classloader
    }
  }

  private val dependencyCache: jutil.Map[Seq[ModuleID], List[Path]] = {
    jutil.Collections.synchronizedMap(new jutil.HashMap())
  }
  private[scalafix] def fetchScalafixDependencies(
      resolvers: Seq[Repository]
  ): function.Function[Seq[ModuleID], List[Path]] =
    new function.Function[Seq[ModuleID], List[Path]] {
      override def apply(t: Seq[ModuleID]): List[Path] = {
        val dependencies = t.map { module =>
          val binarySuffix =
            if (module.crossVersion.isInstanceOf[CrossVersion.Binary]) "_2.12"
            else ""
          new Dependency(
            module.organization,
            module.name + binarySuffix,
            module.revision
          )
        }
        CoursierSmall.fetch(
          fetchSettings
            .withRepositories(resolvers ++: fetchSettings.repositories)
            .withDependencies(scalafixCli :: dependencies.toList)
        )
      }
    }

  private val silentCoursierWriter = new OutputStreamWriter(System.out) {
    override def write(str: String): Unit = {
      if (str.endsWith(".pom\n") || str.endsWith(".pom.sha1\n")) {
        () // Ignore noisy "Downloading $URL.pom" logs that appear even for cached artifacts
      } else {
        super.write(str)
      }
    }
  }

  private val fetchSettings = new coursiersmall.Settings()
    .withRepositories(
      List(
        Repository.MavenCentral,
        Repository.Ivy2Local,
        Repository.SonatypeReleases,
        Repository.SonatypeSnapshots
      )
    )
    .withWriter(silentCoursierWriter)
    // Scalafix SNAPSHOT releases always use a new version number so it's  safe to use infinity here.
    .withTtl(Some(Duration.Inf))
    // For custom external rules to use the same Scalafix version as this plugin instead of
    // the (presumably) older Scalafix version that the custom rules depend on.
    .withForceVersions(List(scalafixCli))

}
