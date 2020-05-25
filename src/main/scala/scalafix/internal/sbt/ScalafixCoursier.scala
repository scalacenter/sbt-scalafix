package scalafix.internal.sbt

import java.nio.file.Path
import java.util.function
import java.{util => jutil}

import coursierapi._
import com.geirsson.{coursiersmall => cs}
import sbt._
import scalafix.sbt.BuildInfo
import scala.collection.JavaConverters._

object ScalafixCoursier {
  private def scalafixCliModule: Module =
    Module.of(
      "ch.epfl.scala",
      s"scalafix-cli_${BuildInfo.scala212}"
    )
  private def scalafixCli: Dependency =
    Dependency.of(
      scalafixCliModule,
      BuildInfo.scalafixVersion
    )

  def scalafixCliJars(
      repositories: Seq[cs.Repository]
  ): List[Path] = {
    runFetch(
      newFetch()
        .addDependencies(scalafixCli)
        .addRepositories(toCoursierRepositories(repositories): _*)
    )
  }

  def scalafixToolClasspath(
      deps: Seq[ModuleID],
      customResolvers: Seq[cs.Repository]
  ): Seq[URL] = {
    if (deps.isEmpty) {
      Nil
    } else {
      val jars = dependencyCache.computeIfAbsent(
        deps,
        fetchScalafixDependencies(customResolvers)
      )
      jars.map(_.toUri.toURL)
    }
  }

  private val dependencyCache: jutil.Map[Seq[ModuleID], List[Path]] = {
    jutil.Collections.synchronizedMap(new jutil.HashMap())
  }
  private[scalafix] def fetchScalafixDependencies(
      customResolvers: Seq[cs.Repository]
  ): function.Function[Seq[ModuleID], List[Path]] =
    new function.Function[Seq[ModuleID], List[Path]] {
      override def apply(t: Seq[ModuleID]): List[Path] = {
        val dependencies = t.map { module =>
          val binarySuffix =
            if (module.crossVersion.isInstanceOf[CrossVersion.Binary]) "_2.12"
            else ""
          Dependency.of(
            module.organization,
            module.name + binarySuffix,
            module.revision
          )
        }
        runFetch(
          newFetch()
            .addRepositories(toCoursierRepositories(customResolvers): _*)
            .addDependencies(scalafixCli)
            .addDependencies(dependencies.toArray: _*)
        )
      }
    }

  def toCoursierRepositories(
      repositories: Seq[cs.Repository]
  ): Array[Repository] =
    repositories.iterator.collect {
      case m: cs.Repository.Maven => coursierapi.MavenRepository.of(m.root)
      case i: cs.Repository.Ivy => coursierapi.IvyRepository.of(i.root)
      case cs.Repository.Ivy2Local => coursierapi.Repository.ivy2Local()
    }.toArray
  def runFetch(fetch: Fetch): List[Path] =
    fetch.fetch().asScala.iterator.map(_.toPath()).toList
  def newFetch(): Fetch =
    Fetch
      .create()
      .withRepositories()
      .withResolutionParams(
        ResolutionParams
          .create()
          .withForceVersions(
            Map(
              scalafixCliModule -> scalafixCli.getVersion()
            ).asJava
          )
      )

  val defaultResolvers: Seq[cs.Repository] = Seq(
    cs.Repository.Ivy2Local,
    cs.Repository.MavenCentral,
    new cs.Repository.Maven(
      "https://oss.sonatype.org/content/repositories/public"
    ),
    cs.Repository.SonatypeSnapshots
  )
}
