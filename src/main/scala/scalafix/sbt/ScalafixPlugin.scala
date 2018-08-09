package scalafix.sbt

import com.geirsson.coursiersmall
import com.geirsson.coursiersmall.CoursierSmall
import com.geirsson.coursiersmall.Dependency
import com.geirsson.coursiersmall.Repository
import java.io.OutputStreamWriter
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.Properties
import java.util.function
import java.{util => jutil}
import sbt.Def
import sbt.Keys._
import sbt._
import sbt.complete.Parser
import sbt.plugins.JvmPlugin
import scalafix.internal.sbt.ScalafixCompletions
import scalafix.interfaces.{Scalafix => ScalafixAPI}
import scala.collection.JavaConverters._
import scalafix.interfaces.ScalafixMainArgs
import scalafix.interfaces.ScalafixMainMode

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
    val scalafixCli: InputKey[Unit] =
      inputKey[Unit](
        "Run the scalafix cli in this project and configuration. " +
          "For example: scalafix -r RemoveUnusedImports. " +
          "To run on test sources use test:scalafixCli."
      )
    val scalafixTest: InputKey[Unit] =
      inputKey[Unit](
        "The same as scalafix except report an error for unfixed files without modifying files. " +
          "Useful to enforce scalafix in CI."
      )
    val scalafixAutoSuppressLinterErrors: InputKey[Unit] =
      inputKey[Unit](
        "Run scalafix and automatically suppress linter errors " +
          "by inserting /* scalafix:ok */ comments into the source code. " +
          "Useful when migrating an existing large codebase with many linter errors."
      )
    val sbtfix: InputKey[Unit] =
      inputKey[Unit](
        "The same as scalafix except it runs on *.sbt and project/*.scala files. " +
          "Note, semantic rewrites such as ExplicitResultTypes and RemoveUnusedImports are not supported."
      )
    val sbtfixTest: InputKey[Unit] =
      inputKey[Unit](
        "Same as scalafixTest except for *.sbt and project/*.scala files. " +
          "Useful to enforce scalafix in CI."
      )
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

    lazy val scalafixConfigSettings: Seq[Def.Setting[_]] = Seq(
      scalafix := scalafixInputTask(
        scalafixParserCompat,
        compat = true,
        ScalafixMainMode.IN_PLACE
      ).tag(Scalafix).evaluated,
      scalafixTest := scalafixInputTask(
        scalafixParserCompat,
        compat = true,
        ScalafixMainMode.TEST
      ).tag(Scalafix).evaluated,
      scalafixCli := scalafixInputTask(
        scalafixParser,
        compat = false,
        ScalafixMainMode.IN_PLACE
      ).tag(Scalafix).evaluated,
      scalafixAutoSuppressLinterErrors := scalafixInputTask(
        scalafixParser,
        compat = true,
        ScalafixMainMode.AUTO_SUPPRESS_LINTER_ERRORS
      ).tag(Scalafix).evaluated
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
    @deprecated("This setting is no longer used", "0.6.0")
    def sbtfixSettings: Seq[Def.Setting[_]] =
      Nil
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

  def classloadScalafixAPI(
      toolClasspathDeps: Seq[ModuleID]): (ScalafixAPI, ScalafixMainArgs) = {
    val dep = new Dependency(
      "ch.epfl.scala",
      s"scalafix-cli_${BuildInfo.scala212}",
      BuildInfo.scalafix
    )
    val jars = CoursierSmall.fetch(fetchSettings.withDependencies(List(dep)))
    val urls = jars.map(_.toUri.toURL).toArray
    val classloader = new URLClassLoader(urls, this.getClass.getClassLoader)
    val api = ScalafixAPI.classloadInstance(classloader)
    val toolClasspath = scalafixToolClasspath(toolClasspathDeps, classloader)
    val args = api
      .newMainArgs()
      .withToolClasspath(toolClasspath)
    (api, args)
  }

  override def projectSettings: Seq[Def.Setting[_]] =
    Seq(Compile, Test).flatMap(inConfig(_)(scalafixConfigSettings))

  override def globalSettings: Seq[Def.Setting[_]] = Seq(
    scalafixConfig := Option(file(".scalafix.conf")).filter(_.isFile),
    scalafixVerbose := false,
    commands += ScalafixEnable.command,
    sbtfix := sbtfixImpl(compat = true, ScalafixMainMode.IN_PLACE).evaluated,
    sbtfixTest := sbtfixImpl(compat = true, ScalafixMainMode.TEST).evaluated,
    aggregate.in(sbtfix) := false,
    aggregate.in(sbtfixTest) := false,
    scalafixSemanticdbVersion := BuildInfo.scalameta,
    scalafixDependencies := Nil
  )

  def scalafixToolClasspath(
      deps: Seq[ModuleID],
      parent: ClassLoader): URLClassLoader = {
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
    classloadScalafixAPI(scalafixDependencies.value)
  }

  private def sbtfixImpl(
      compat: Boolean,
      mode: ScalafixMainMode
  ): Def.Initialize[InputTask[Unit]] = {
    Def.inputTaskDyn {
      val baseDir = baseDirectory.in(ThisBuild).value
      val sbtDir = baseDir./("project").toPath
      val sbtFiles = baseDir.*("*.sbt").get.map(_.toPath)
      scalafixMainTask(
        inputArgs = scalafixParserCompat.parsed,
        compat = compat,
        mode = mode,
        files = sbtDir +: sbtFiles,
        projectId = "sbt-build",
        streams = streams.value
      )
    }
  }

  // hack to avoid illegal dynamic reference, can't figure out how to do
  // scalafixParser(baseDirectory.in(ThisBuild).value).parsed
  private def workingDirectory: File = file(sys.props("user.dir"))

  private val scalafixParser =
    ScalafixCompletions.parser(workingDirectory.toPath, compat = false)
  private val scalafixParserCompat =
    ScalafixCompletions.parser(workingDirectory.toPath, compat = true)

  def scalafixInputTask(
      parser: Parser[Seq[String]],
      compat: Boolean,
      mode: ScalafixMainMode
  ): Def.Initialize[InputTask[Unit]] =
    Def.inputTaskDyn {
      scalafixCompileTask(parser.parsed, compat, mode)
    }

  def scalafixCompileTask(
      inputArgs: Seq[String],
      compat: Boolean,
      mode: ScalafixMainMode
  ): Def.Initialize[Task[Unit]] =
    Def.taskDyn {
      compile.value // trigger compilation
      val scalafixClasspath =
        classDirectory.value.toPath +:
          dependencyClasspath.value.map(_.data.toPath)
      val sourcesToFix = for {
        source <- unmanagedSources.in(scalafix).value
        if source.exists()
        if canFix(source)
      } yield source.toPath
      scalafixMainTask(
        inputArgs,
        compat,
        mode,
        sourcesToFix,
        thisProject.value.id,
        streams.value,
        scalafixClasspath
      )
    }

  def scalafixMainTask(
      inputArgs: Seq[String],
      compat: Boolean,
      mode: ScalafixMainMode,
      files: Seq[Path],
      projectId: String,
      streams: TaskStreams,
      classpath: Seq[Path] = Nil
  ): Def.Initialize[Task[Unit]] = {
    if (files.isEmpty) Def.task(())
    else {
      Def.task {
        val (api, baseArgs) = scalafixAPI.value
        var mainArgs = baseArgs
          .withMode(mode)
          .withPaths(files.asJava)
          .withClasspath(classpath.asJava)

        if (scalafixVerbose.value) {
          mainArgs = mainArgs.withArgs(List("--verbose").asJava)
        }

        scalafixConfig.value match {
          case Some(x) =>
            mainArgs = mainArgs.withConfig(x.toPath)
          case _ =>
        }

        if (compat && inputArgs.nonEmpty) {
          mainArgs = mainArgs.withRules(inputArgs.asJava)
        }

        if (files.lengthCompare(1) > 0) {
          streams.log.info(s"Running scalafix on ${files.size} Scala sources")
        }

        val errors = api.runMain(mainArgs)
        if (errors.nonEmpty) {
          throw new MessageOnlyException(errors.mkString(", "))
        }
      }
    }
  }

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
