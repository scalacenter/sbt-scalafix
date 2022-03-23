package scalafix.sbt

import sbt._
import sbt.Keys._

/** Command to automatically enable semanticdb compiler output for shell session
  */
object ScalafixEnable {

  /** If the provided Scala binary version is supported, return the full version
    * required for running semanticdb-scalac.
    */
  private lazy val semanticdbScalacFullScalaVersion
      : PartialFunction[(Long, Long), String] = (for {
    v <- BuildInfo.supportedScalaVersions
    p <- CrossVersion.partialVersion(v).toList
  } yield p -> v).toMap

  /** If the provided Scala binary version is supported, return the full version
    * required for running semanticdb-scalac or None if support is built-in in
    * the compiler and the full version does not need to be adjusted.
    */
  private lazy val maybeSemanticdbScalacFullScalaVersion
      : PartialFunction[(Long, Long), Option[String]] =
    semanticdbScalacFullScalaVersion.andThen(Some.apply).orElse {
      // semanticdb is built-in in the Scala 3 compiler
      case (major, _) if major == 3 => None
    }

  /** Collect projects across the entire build, using the partial function
    * accepting a Scala binary version
    */
  private def collectProjects[U](
      extracted: Extracted,
      pf: PartialFunction[(Long, Long), U]
  ): Seq[(ProjectRef, U)] = for {
    p <- extracted.structure.allProjectRefs
    version <- (p / scalaVersion).get(extracted.structure.data).toList
    partialVersion <- CrossVersion.partialVersion(version).toList
    res <- pf.lift(partialVersion).toList
  } yield p -> res

  lazy val command = Command.command(
    "scalafixEnable",
    briefHelp = "Configure SemanticdbPlugin for scalafix.",
    detail = """1. set semanticdbEnabled & semanticdbVersion
      |2. conditionally sets scalaVersion when support is not built-in in the compiler""".stripMargin
  ) { s =>
    val extracted = Project.extract(s)
    val scalacOptionsSettings = Seq(Compile, Test).flatMap(
      inConfig(_)(ScalafixPlugin.relaxScalacOptionsConfigSettings)
    )
    val settings = for {
      (p, maybeFullVersion) <- collectProjects(
        extracted,
        maybeSemanticdbScalacFullScalaVersion
      )
      enableSemanticdbPlugin <- maybeFullVersion.toList.map { fullVersion =>
        scalaVersion := fullVersion,
      } :+ (semanticdbEnabled := true)
      settings <-
        inScope(ThisScope.copy(project = Select(p)))(
          scalacOptionsSettings ++ enableSemanticdbPlugin
        ) :+ (Global / semanticdbVersion := BuildInfo.scalametaVersion)
    } yield settings
    extracted.appendWithoutSession(settings, s)
  }
}
