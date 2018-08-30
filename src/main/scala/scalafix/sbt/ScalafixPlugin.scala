package scalafix.sbt

import scalafix.interfaces._
import scalafix.interfaces.{Scalafix => ScalafixAPI}
import scalafix.internal.sbt.ScalafixCompletions
import scalafix.internal.sbt.ScalafixInterfacesClassloader
import sbt.internal.sbtscalafix.JLineAccess

import com.geirsson.coursiersmall
import com.geirsson.coursiersmall._

import sbt._
import sbt.complete.Parser
import sbt.Def
import sbt.internal.sbtscalafix.Compat
import sbt.Keys._
import sbt.plugins.JvmPlugin

import scala.collection.JavaConverters._

import java.io.OutputStreamWriter
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.function
import java.util.Properties
import java.{util => jutil}

object ScalafixPlugin extends AutoPlugin {
  override def trigger: PluginTrigger = allRequirements
  override def requires: Plugins = JvmPlugin
  object autoImport {
    val Scalafix = Tags.Tag("scalafix")

    val scalafix: InputKey[Unit] =
      inputKey[Unit](
        "Run scalafix rule in this project and configuration. " +
          "For example: scalafix RemoveUnusedImports. " +
          "To run on test sources use test:scalafix."
      )
    val scalafixTest: TaskKey[Unit] =
      taskKey[Unit]("Shorthand for 'scalafix --test'")
    val scalafixDependencies: SettingKey[Seq[ModuleID]] =
      settingKey[Seq[ModuleID]](
        "Optional list of custom rules to install from Maven Central. " +
          "This setting is read from the global scope so it only needs to be defined once in the build."
      )
    val scalafixConfig: SettingKey[Option[File]] =
      settingKey[Option[File]](
        "Optional location to .scalafix.conf file to specify which scalafix rules should run. " +
          "Defaults to the build base directory if a .scalafix.conf file exists."
      )
    val scalafixSemanticdbVersion: SettingKey[String] = settingKey[String](
      s"Which version of semanticdb to use. Default is ${BuildInfo.scalameta}."
    )
    val scalafixSemanticdb: ModuleID =
      scalafixSemanticdb(BuildInfo.scalameta)
    def scalafixSemanticdb(scalametaVersion: String): ModuleID =
      "org.scalameta" % "semanticdb-scalac" % scalametaVersion cross CrossVersion.full
    val scalafixVerbose: SettingKey[Boolean] =
      settingKey[Boolean]("pass --verbose to scalafix")

    def scalafixConfigSettings(config: Configuration): Seq[Def.Setting[_]] =
      Seq(
        scalafix := scalafixCompileTask(config).tag(Scalafix).evaluated,
        scalafixTest := scalafix.toTask(" --test").value
      )

    @deprecated("This setting is no longer used", "0.6.0")
    val scalafixSourceroot: SettingKey[File] =
      settingKey[File]("Unused")
    @deprecated("Use scalacOptions += -Yrangepos instead", "0.6.0")
    def scalafixScalacOptions: Def.Initialize[Seq[String]] =
      Def.setting(Nil)
    @deprecated("Use addCompilerPlugin(semanticdb-scalac) instead", "0.6.0")
    def scalafixLibraryDependencies: Def.Initialize[List[ModuleID]] =
      Def.setting(Nil)

    @deprecated(
      "Use addCompilerPlugin(scalafixSemanticdb) and scalacOptions += \"-Yrangepos\" instead",
      "0.6.0"
    )
    def scalafixSettings: Seq[Def.Setting[_]] =
      Nil
  }
  import autoImport._

  lazy val props: Properties = {
    val props = new Properties()
    Option(this.getClass.getResourceAsStream("sbt-scalafix.properties")) match {
      case Some(stream) =>
        props.load(stream)
      case None =>
        println("error: failed to load sbt-scalafix-properties")
    }
    props
  }

  def scalafixMainCallback(logger: Logger): ScalafixMainCallback =
    new ScalafixMainCallback {
      def fullStringID(lintID: ScalafixLintID): String =
        if (lintID.categoryID().isEmpty) lintID.ruleName()
        else if (lintID.ruleName().isEmpty) lintID.categoryID()
        else s"${lintID.ruleName()}.${lintID.categoryID()}"
      override def reportDiagnostic(diagnostic: ScalafixDiagnostic): Unit = {
        def formatMessage: String = {
          val prefix =
            if (diagnostic.lintID().isPresent) {
              val id = fullStringID(diagnostic.lintID().get)
              s"[$id] "
            } else {
              ""
            }
          val message = prefix + diagnostic.message()

          if (diagnostic.position().isPresent) {
            val severity =
              diagnostic.severity().toString.toLowerCase()
            diagnostic.position().get().formatMessage(severity, message)
          } else {
            message
          }
        }
        diagnostic.severity() match {
          case ScalafixSeverity.INFO => logger.info(formatMessage)
          case ScalafixSeverity.WARNING => logger.warn(formatMessage)
          case ScalafixSeverity.ERROR => logger.error(formatMessage)
        }
      }
    }

  def classloadScalafixAPI(
      logger: Logger,
      toolClasspathDeps: Seq[ModuleID]
  ): (ScalafixAPI, ScalafixMainArgs) = {
    val dep = new Dependency(
      "ch.epfl.scala",
      s"scalafix-cli_${BuildInfo.scala212}",
      BuildInfo.scalafix
    )
    val jars = CoursierSmall.fetch(fetchSettings.withDependencies(List(dep)))
    val urls = jars.map(_.toUri.toURL).toArray
    val interfacesParent = new ScalafixInterfacesClassloader(
      this.getClass.getClassLoader
    )
    val classloader = new URLClassLoader(urls, interfacesParent)
    val api = ScalafixAPI.classloadInstance(classloader)
    val toolClasspath = scalafixToolClasspath(toolClasspathDeps, classloader)
    val callback = scalafixMainCallback(logger)
    val args = api
      .newMainArgs()
      .withToolClasspath(toolClasspath)
      .withMainCallback(callback)
    (api, args)
  }

  override def projectSettings: Seq[Def.Setting[_]] =
    Seq(Compile, Test).flatMap(c => inConfig(c)(scalafixConfigSettings(c)))

  override def globalSettings: Seq[Def.Setting[_]] = Seq(
    scalafixConfig := Option(file(".scalafix.conf")).filter(_.isFile),
    scalafixVerbose := false,
    commands += ScalafixEnable.command,
    scalafixSemanticdbVersion := BuildInfo.scalameta,
    scalafixDependencies := Nil
  )

  def scalafixToolClasspath(
      deps: Seq[ModuleID],
      parent: ClassLoader
  ): URLClassLoader = {
    if (deps.isEmpty) {
      new URLClassLoader(Array(), parent)
    } else {
      val jars =
        dependencyCache.computeIfAbsent(deps, fetchScalafixDependencies)
      val urls = jars.map(_.toUri.toURL).toArray
      val classloader = new URLClassLoader(urls, parent)
      classloader
    }
  }

  val scalafixAPI = Def.setting {
    // construct custom logger so that `scalafixAPI` can be a setting instead of task.
    val logger = Compat.ConsoleLogger(System.out)
    classloadScalafixAPI(logger, scalafixDependencies.value)
  }

  val loadedRules = Def.setting {
    val (_, baseArgs) = scalafixAPI.value
    baseArgs.availableRules.asScala.toList
  }

  def scalafixCompileTask(
      config: Configuration
  ): Def.Initialize[InputTask[Unit]] =
    Def
      .inputTask {
        val scalafixClasspath =
          classDirectory.in(config).value.toPath +:
            dependencyClasspath.in(config).value.map(_.data.toPath)

        val sourcesToFix = for {
          source <- unmanagedSources.in(config).in(scalafix).value
          if source.exists()
          if canFix(source)
        } yield source.toPath

        val (api, baseArgs) = scalafixAPI.value

        var mainArgs = baseArgs
          .withPaths(sourcesToFix.asJava)
          .withClasspath(scalafixClasspath.asJava)

        if (scalafixVerbose.value) {
          mainArgs = mainArgs.withArgs(List("--verbose").asJava)
        }

        scalafixConfig.value match {
          case Some(x) =>
            mainArgs = mainArgs.withConfig(x.toPath)
          case _ =>
        }
        val logger = streams.value.log

        val inputArgs = new ScalafixCompletions(
          workingDirectory = baseDirectory.in(ThisBuild).value.toPath,
          loadedRules = loadedRules.value,
          terminalWidth = Some(JLineAccess.terminalWidth)
        ).parser.parsed

        if (sourcesToFix.nonEmpty) {
          if (inputArgs.nonEmpty) {
            mainArgs = mainArgs.withArgs(inputArgs.asJava)
          }

          if (sourcesToFix.lengthCompare(1) > 0) {
            logger.info(
              s"Running scalafix on ${sourcesToFix.size} Scala sources"
            )
          }

          val errors = api.runMain(mainArgs)
          if (errors.nonEmpty) {
            throw new ScalafixFailed(errors.toList)
          }
        }
      }
      .dependsOn(compile.in(config)) // trigger compilation

  private def canFix(file: File): Boolean = {
    val path = file.getPath
    path.endsWith(".scala") ||
    path.endsWith(".sbt")
  }

  private val dependencyCache: jutil.Map[Seq[ModuleID], List[Path]] = {
    jutil.Collections.synchronizedMap(new jutil.HashMap())
  }

  private[scalafix] val fetchScalafixDependencies =
    new function.Function[Seq[ModuleID], List[Path]] {
      override def apply(t: Seq[ModuleID]): List[Path] = {
        val dependencies = t.map { module =>
          new Dependency(module.organization, module.name, module.revision)
        }
        CoursierSmall.fetch(fetchSettings.withDependencies(dependencies.toList))
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
        Repository.SonatypeReleases,
        Repository.SonatypeSnapshots,
        Repository.Ivy2Local
      )
    )
    .withWriter(silentCoursierWriter)

}
