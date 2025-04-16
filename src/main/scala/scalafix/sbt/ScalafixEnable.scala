package scalafix.sbt

import sbt.*
import sbt.Keys.*

import coursierapi.Repository

import ScalafixPlugin.autoImport.*

/** Command to automatically prepare the build for scalafix invocations */
object ScalafixEnable {

  private def latestSemanticdbScalac(
      scalaVersion: String,
      repositories: Seq[Repository]
  ): Option[String] = {
    val semanticdbScalacModule = coursierapi.Module.parse(
      "org.scalameta:::semanticdb-scalac",
      coursierapi.ScalaVersion.of(scalaVersion)
    )
    Option(
      coursierapi.Versions.create
        .withModule(semanticdbScalacModule)
        .withRepositories(repositories*)
        .versions()
        .getMergedListings
        .getRelease
    ).filter(_.nonEmpty)
  }

  private def collectProjects(extracted: Extracted): Seq[CompatibleProject] = {
    val repositories = (ThisBuild / scalafixResolvers)
      .get(extracted.structure.data)
      .getOrElse(Seq.empty)
    for {
      (scalaVersion, projectRefs) <-
        extracted.structure.allProjectRefs
          .flatMap { projectRef =>
            (projectRef / scalaVersion)
              .get(extracted.structure.data)
              .map(sv => (sv, projectRef))
          }
          .foldLeft(Map.empty[String, Seq[ProjectRef]]) {
            case (acc, (sv, projectRef)) =>
              acc.updated(sv, acc.getOrElse(sv, Seq.empty) :+ projectRef)
          }
          .toSeq
      maybeSemanticdbVersion <-
        if (scalaVersion.startsWith("2."))
          latestSemanticdbScalac(scalaVersion, repositories) match {
            case None =>
              Seq.empty // don't activate semanticdb
            case Some(version) =>
              Seq(Some(version)) // activate semanticdb with plugin
          }
        else Seq(None) // activate semanticdb without plugin
      projectRef <- projectRefs
    } yield CompatibleProject(projectRef, maybeSemanticdbVersion)
  }

  private case class CompatibleProject(
      ref: ProjectRef,
      semanticdbVersion: Option[String]
  )

  lazy val command: Command = Command.command(
    "scalafixEnable",
    briefHelp =
      "Configure SemanticdbPlugin for scalafix on supported projects.",
    detail = """1. set scalafixAllowDynamicFallback := true
      |2. set semanticdbEnabled := true
      |3. for scala 2.x, set semanticdbVersion to the latest scalameta version""".stripMargin
  ) { s =>
    val extracted = Project.extract(s)
    val scalacOptionsSettings = Seq(Compile, Test).flatMap(
      inConfig(_)(ScalafixPlugin.relaxScalacOptionsConfigSettings)
    )
    val dynamicFallbackSetting =
      scalafixAllowDynamicFallback := true
    val settings = for {
      project <- collectProjects(extracted)
      semanticdbPluginSettings <- (semanticdbEnabled := true) +:
        project.semanticdbVersion.map { version =>
          semanticdbVersion := version
        }.toSeq
      settings <- inScope(ThisScope.copy(project = Select(project.ref)))(
        scalacOptionsSettings ++ semanticdbPluginSettings :+ dynamicFallbackSetting
      )
    } yield settings
    extracted.appendWithSession(settings, s)
  }
}
