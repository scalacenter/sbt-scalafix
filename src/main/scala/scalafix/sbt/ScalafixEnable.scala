package scalafix.sbt

import sbt._
import sbt.Keys._
import sbt.internal.inc.ScalaInstance
import scalafix.internal.sbt.SemanticdbPlugin

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

  lazy val command =
    if (SemanticdbPlugin.available) withSemanticdbPlugin
    else withSemanticdbScalac

  private lazy val withSemanticdbPlugin = Command.command(
    "scalafixEnable",
    briefHelp = "Configure SemanticdbPlugin for scalafix.",
    detail = """1. set semanticdbEnabled where supported
      |2. conditionally sets semanticdbVersion & scalaVersion when support is not built-in in the compiler""".stripMargin
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
      enableSemanticdbPlugin <- maybeFullVersion.toList.flatMap { fullVersion =>
        List(
          scalaVersion := fullVersion,
          SemanticdbPlugin.semanticdbVersion := BuildInfo.scalametaVersion
        )
      } :+ (SemanticdbPlugin.semanticdbEnabled := true)
      settings <-
        inScope(ThisScope.copy(project = Select(p)))(
          scalacOptionsSettings ++ enableSemanticdbPlugin
        )
    } yield settings
    extracted.appendWithoutSession(settings, s)
  }

  private lazy val withSemanticdbScalac = Command.command(
    "scalafixEnable",
    briefHelp =
      "Configure libraryDependencies, scalaVersion and scalacOptions for scalafix.",
    detail = """1. enables the semanticdb-scalac compiler plugin
      |2. sets scalaVersion to latest Scala version supported by scalafix
      |3. add -Yrangepos to Compile|Test / compile / scalacOptions""".stripMargin
  ) { s =>
    val extracted = Project.extract(s)
    val scalacOptionsSettings = Seq(Compile, Test).flatMap(
      inConfig(_)(
        semanticdbConfigSettings ++
          ScalafixPlugin.relaxScalacOptionsConfigSettings
      )
    )
    val settings: Seq[Setting[_]] = for {
      (p, fullVersion) <- collectProjects(
        extracted,
        semanticdbScalacFullScalaVersion
      )
      isSemanticdbEnabled =
        (p / libraryDependencies)
          .get(extracted.structure.data)
          .exists(_.exists(_.name == "semanticdb-scalac"))
      addSemanticdbCompilerPlugin <- List(
        p / scalaVersion := fullVersion,
        p / libraryDependencies += compilerPlugin(
          ScalafixPlugin.autoImport.scalafixSemanticdb
        )
      )
      settings <-
        inScope(ThisScope.copy(project = Select(p)))(scalacOptionsSettings) ++
          (if (!isSemanticdbEnabled) addSemanticdbCompilerPlugin else List())
    } yield settings
    extracted.appendWithoutSession(settings, s)
  }

  private val semanticdbConfigSettings: Seq[Def.Setting[_]] =
    Seq(
      compile / scalacOptions := {
        val old = (compile / scalacOptions).value
        val options = List(
          "-Yrangepos",
          "-Xplugin-require:semanticdb"
        )
        // don't rely on autoCompilerPlugins to inject the plugin as it does not work if scalacOptions is overriden in the build
        val plugins =
          Classpaths.autoPlugins(
            update.value,
            Seq(),
            ScalaInstance.isDotty(scalaVersion.value)
          )
        old ++ (plugins ++ options).diff(old)
      }
    )
}
