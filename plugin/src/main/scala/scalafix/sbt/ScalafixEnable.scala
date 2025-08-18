package scalafix.sbt

import scala.jdk.CollectionConverters.*
import scala.util.*

import sbt.*
import sbt.Keys.*
import sbt.VersionNumber.SemVer

import coursierapi.Repository

import ScalafixPlugin.autoImport.scalafixResolvers

/** Command to automatically enable semanticdb compiler output for shell session
  */
object ScalafixEnable {

  /** If the provided Scala binary version is supported, return the latest scala
    * full version for which the recommended semanticdb-scalac is available
    */
  private lazy val recommendedSemanticdbScalacScalaVersion
      : PartialFunction[(Long, Long), VersionNumber] = (for {
    v <- BuildInfo.supportedScalaVersions
    p <- CrossVersion.partialVersion(v).toList
  } yield p -> VersionNumber(v)).toMap

  /** If the provided Scala binary version is supported, return the latest scala
    * full version for which the recommended semanticdb-scalac is available, or
    * None if semanticdb support is built-in in the compiler
    */
  private lazy val maybeRecommendedSemanticdbScalacScalaVersion
      : PartialFunction[(Long, Long), Option[VersionNumber]] =
    recommendedSemanticdbScalacScalaVersion.andThen(Some.apply).orElse {
      // semanticdb is built-in in the Scala 3 compiler
      case (major, _) if major == 3 => None
    }

  /** Collect compatible projects across the entire build */
  private def collectProjects(extracted: Extracted): Seq[CompatibleProject] =
    for {
      p <- extracted.structure.allProjectRefs
      scalaV <- (p / scalaVersion).get(extracted.structure.data).toList
      partialVersion <- CrossVersion.partialVersion(scalaV).toList
      maybeRecommendedSemanticdbScalacV <-
        maybeRecommendedSemanticdbScalacScalaVersion.lift(partialVersion).toList
      scalafixResolvers0 <- (p / scalafixResolvers)
        .get(extracted.structure.data)
        .toList
      semanticdbCompilerPlugin0 <- (p / semanticdbCompilerPlugin)
        .get(extracted.structure.data)
        .toList
    } yield CompatibleProject(
      p,
      VersionNumber(scalaV),
      semanticdbCompilerPlugin0,
      scalafixResolvers0,
      maybeRecommendedSemanticdbScalacV
    )

  private case class CompatibleProject(
      ref: ProjectRef,
      scalaVersion0: VersionNumber,
      semanticdbCompilerPlugin0: ModuleID,
      scalafixResolvers0: Seq[Repository],
      maybeRecommendedSemanticdbScalacScalaV: Option[VersionNumber]
  )

  lazy val command: Command = Command.command(
    "scalafixEnable",
    briefHelp =
      "Configure SemanticdbPlugin for scalafix on supported projects.",
    detail = """1. set semanticdbEnabled := true
      |2. for scala 2.x,
      |  - set semanticdbCompilerPlugin to the scalameta version tracked by scalafix if available for scalaVersion, 
      |  - otherwise set semanticdbCompilerPlugin to a compatible version available for scalaVersion,
      |  - otherwise force scalaVersion to the latest version supported by the scalameta version tracked by scalafix.""".stripMargin
  ) { s =>
    val extracted = Project.extract(s)
    val scalacOptionsSettings = Seq(Compile, Test).flatMap(
      inConfig(_)(ScalafixPlugin.relaxScalacOptionsConfigSettings)
    )
    val settings = for {
      project <- collectProjects(extracted)
      enableSemanticdbPlugin <-
        project.maybeRecommendedSemanticdbScalacScalaV.toList
          .flatMap { recommendedSemanticdbScalacScalaV =>

            import scalafix.internal.sbt.Implicits._
            val semanticdbScalacModule =
              coursierapi.Dependency
                .parse(
                  project.semanticdbCompilerPlugin0.asCoursierCoordinates,
                  coursierapi.ScalaVersion.of(project.scalaVersion0.toString)
                )
                .getModule
            val recommendedSemanticdbV =
              VersionNumber(BuildInfo.scalametaVersion)
            val compatibleSemanticdbVs = Try(
              coursierapi.Versions.create
                .withRepositories(project.scalafixResolvers0*)
                .withModule(semanticdbScalacModule)
                .versions()
                .getMergedListings
                .getAvailable
                .asScala
                .map(VersionNumber.apply)
                // don't use snapshots
                .filter(_.extras == Nil)
                // https://github.com/scalameta/scalameta/blob/main/COMPATIBILITY.md
                .filter(SemVer.isCompatible(_, recommendedSemanticdbV))
                .toList
            )

            compatibleSemanticdbVs match {
              case Success(Nil) | Failure(_) =>
                Seq(
                  scalaVersion := {
                    val v = recommendedSemanticdbScalacScalaV.toString
                    sLog.value.warn(
                      s"Forcing scalaVersion to $v in project " +
                        s"${project.ref.project} since no semanticdb-scalac " +
                        s"version binary-compatible with $recommendedSemanticdbV " +
                        s"and cross-published for scala " +
                        s"${project.scalaVersion0.toString} was found - " +
                        s"consider bumping scala"
                    )
                    v
                  },
                  semanticdbVersion := recommendedSemanticdbV.toString
                )
              case Success(available)
                  if available.contains(recommendedSemanticdbV) =>
                Seq(
                  semanticdbVersion := recommendedSemanticdbV.toString
                )
              case Success(earliestAvailable :: tail) =>
                val safeRecommendedSemanticdbV =
                  if (recommendedSemanticdbV.toString == "4.13.1.1")
                    VersionNumber("4.13.1")
                  else recommendedSemanticdbV

                val futureVersion =
                  SemanticSelector.apply(s">${safeRecommendedSemanticdbV}")

                if (earliestAvailable.matchesSemVer(futureVersion)) {
                  Seq(
                    semanticdbVersion := {
                      val v = earliestAvailable.toString
                      sLog.value.info(
                        s"Setting semanticdbVersion to $v in project " +
                          s"${project.ref.project} since the version " +
                          s"${recommendedSemanticdbV} tracked by scalafix " +
                          s"${BuildInfo.scalafixVersion} will not be " +
                          s"published for scala " +
                          s"${project.scalaVersion0.toString} - " +
                          s"consider upgrading sbt-scalafix"
                      )
                      v
                    }
                  )
                } else {
                  val latestAvailable =
                    tail.lastOption.getOrElse(earliestAvailable)
                  Seq(
                    semanticdbVersion := {
                      val v = latestAvailable.toString
                      sLog.value.info(
                        s"Setting semanticdbVersion to $v in project " +
                          s"${project.ref.project} since the version " +
                          s"${recommendedSemanticdbV} tracked by scalafix " +
                          s"${BuildInfo.scalafixVersion} is no longer " +
                          s"published for scala " +
                          s"${project.scalaVersion0.toString} - " +
                          s"consider bumping scala"
                      )
                      v
                    }
                  )
                }
            }
          } :+ (semanticdbEnabled := true)
      settings <-
        inScope(ThisScope.copy(project = Select(project.ref)))(
          scalacOptionsSettings ++ enableSemanticdbPlugin
        )
    } yield settings
    extracted.appendWithSession(settings, s)
  }
}
