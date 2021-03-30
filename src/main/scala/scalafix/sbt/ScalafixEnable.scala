package scalafix.sbt

import sbt._
import sbt.Keys._
import sbt.internal.sbtscalafix.Compat

/** Command to automatically enable semanticdb-scalac for shell session */
object ScalafixEnable {

  /** sbt 1.0 and 0.13 compatible implementation of partialVersion */
  private def partialVersion(version: String): Option[(Long, Long)] =
    CrossVersion.partialVersion(version).map { case (a, b) =>
      (a.toLong, b.toLong)
    }

  lazy val partialToFullScalaVersion: Map[(Long, Long), String] = (for {
    v <- BuildInfo.supportedScalaVersions
    p <- partialVersion(v).toList
  } yield p -> v).toMap

  def projectsWithMatchingScalaVersion(
      state: State
  ): Seq[(ProjectRef, String)] = {
    val extracted = Project.extract(state)
    for {
      p <- extracted.structure.allProjectRefs
      version <- scalaVersion.in(p).get(extracted.structure.data).toList
      partialVersion <- partialVersion(version).toList
      fullVersion <- partialToFullScalaVersion.get(partialVersion).toList
    } yield p -> fullVersion
  }

  lazy val command = Command.command(
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
      (p, fullVersion) <- projectsWithMatchingScalaVersion(s)
      isSemanticdbEnabled =
        libraryDependencies
          .in(p)
          .get(extracted.structure.data)
          .exists(_.exists(_.name == "semanticdb-scalac"))
      addSemanticdbCompilerPlugin <- List(
        scalaVersion.in(p) := fullVersion,
        libraryDependencies.in(p) += compilerPlugin(
          ScalafixPlugin.autoImport.scalafixSemanticdb
        )
      )
      settings <-
        inScope(ThisScope.in(p))(scalacOptionsSettings) ++
          (if (!isSemanticdbEnabled) addSemanticdbCompilerPlugin else List())
    } yield settings

    val scalafixReady = Compat.append(extracted, settings, s)

    scalafixReady
  }

  private val semanticdbConfigSettings: Seq[Def.Setting[_]] =
    Seq(
      scalacOptions.in(compile) := {
        val old = scalacOptions.in(compile).value
        val options = List(
          "-Yrangepos",
          "-Xplugin-require:semanticdb"
        )
        // don't rely on autoCompilerPlugins to inject the plugin as it does not work if scalacOptions is overriden in the build
        val plugins = Compat.autoPlugins(update.value, scalaVersion.value)
        old ++ (plugins ++ options).diff(old)
      }
    )
}
