package scalafix.sbt

import java.nio.file.Path
import java.{util => jutil}

import com.geirsson.coursiersmall.Repository
import sbt.Keys._
import sbt.internal.sbtscalafix.{Compat, JLineAccess}
import sbt.plugins.JvmPlugin
import sbt.{Def, _}
import scalafix.interfaces._
import scalafix.internal.sbt._

import scala.collection.JavaConverters._
import scala.util.control.NonFatal

object ScalafixPlugin extends AutoPlugin {
  override def trigger: PluginTrigger = allRequirements
  override def requires: Plugins = JvmPlugin

  object autoImport {
    val Scalafix = Tags.Tag("scalafix")

    val ScalafixConfig = config("scalafix")
      .describedAs("Dependencies required for rule execution.")

    val scalafix: InputKey[Unit] =
      inputKey[Unit](
        "Run scalafix rule in this project and configuration. " +
          "For example: scalafix RemoveUnusedImports. " +
          "To run on test sources use test:scalafix."
      )
    val scalafixResolvers: SettingKey[Seq[Repository]] =
      settingKey[Seq[Repository]](
        "Optional list of Maven/Ivy repositories to use for fetching custom rules."
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
    val scalafixSemanticdb: ModuleID =
      scalafixSemanticdb(BuildInfo.scalametaVersion)
    def scalafixSemanticdb(scalametaVersion: String): ModuleID =
      "org.scalameta" % "semanticdb-scalac" % scalametaVersion cross CrossVersion.full

    def scalafixConfigSettings(config: Configuration): Seq[Def.Setting[_]] =
      Seq(
        scalafix := scalafixInputTask(config).evaluated
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

  override lazy val projectConfigurations: Seq[Configuration] =
    Seq(ScalafixConfig)

  override lazy val projectSettings: Seq[Def.Setting[_]] =
    Seq(Compile, Test).flatMap(c => inConfig(c)(scalafixConfigSettings(c))) ++
      inConfig(ScalafixConfig)(Defaults.configSettings) ++
      Seq(ivyConfigurations += ScalafixConfig)

  override lazy val globalSettings: Seq[Def.Setting[_]] = Seq(
    initialize := {
      val _ = initialize.value
      // Ideally, we would not resort to storing mutable state in `initialize`.
      // The optimal solution would be to run `scalafixDependencies.value`
      // inside `scalafixInputTask`.
      // However, we can't do that due to an sbt bug:
      //   https://github.com/sbt/sbt/issues/3572#issuecomment-417582703
      workingDirectory = baseDirectory.in(ThisBuild).value.toPath
      scalafixInterface = ScalafixInterface.fromToolClasspath(
        scalafixDependencies = scalafixDependencies.in(ThisBuild).value,
        scalafixCustomResolvers = scalafixResolvers.in(ThisBuild).value
      )
    },
    scalafixConfig := None, // let scalafix-cli try to infer $CWD/.scalafix.conf
    scalafixResolvers := ScalafixCoursier.defaultResolvers,
    scalafixDependencies := Nil,
    commands += ScalafixEnable.command
  )

  lazy val stdoutLogger = Compat.ConsoleLogger(System.out)
  private var workingDirectory = file("").getAbsoluteFile.toPath
  private var scalafixInterface: () => ScalafixInterface =
    () => throw new UninitializedError

  private def scalafixArgs(): ScalafixArguments = scalafixInterface().args
  private def scalafixArgsFromShell(
      shell: ShellArgs,
      projectDepsExternal: Seq[ModuleID],
      baseResolvers: Seq[Repository],
      projectDepsInternal: Seq[File]
  ) = {
    val (dependencyRules, rules) =
      shell.rules.partition(_.startsWith("dependency:"))
    val parsed = dependencyRules.map {
      case DependencyRule.Parsed(ruleName, org, art, ver) =>
        DependencyRule(ruleName, org %% art % ver)
      case els =>
        stdoutLogger.error(
          s"""|Invalid rule:    $els
              |Expected format: ${DependencyRule.format}
              |""".stripMargin
        )
        throw new ScalafixFailed(List(ScalafixError.CommandLineError))
    }
    val rulesDepsExternal = parsed.map(_.dependency)
    val interface = scalafixInterface().addToolClasspath(
      rulesDepsExternal ++ projectDepsExternal,
      baseResolvers,
      projectDepsInternal
    )
    val newShell = shell.copy(rules = rules ++ parsed.map(_.ruleName))
    (newShell, interface.args)

  }

  private def scalafixInputTask(
      config: Configuration
  ): Def.Initialize[InputTask[Unit]] =
    Def.inputTaskDyn {
      val shell0 = new ScalafixCompletions(
        workingDirectory = () => workingDirectory,
        // Unfortunately, local rules will not show up as completions in the parser, as that parser can only depend
        // on global settings (see note in initialize), while local rules classpath must
        // be looked up via tasks on projects
        loadedRules = () => scalafixArgs().availableRules().asScala,
        terminalWidth = Some(JLineAccess.terminalWidth)
      ).parser.parsed

      val projectDepsInternal =
        internalDependencyClasspath.in(ScalafixConfig).value.map(_.data)
      val projectDepsExternal =
        externalDependencyClasspath
          .in(ScalafixConfig)
          .value
          .flatMap(_.get(moduleID.key))

      if (shell0.rules.isEmpty && shell0.extra == List("--help")) {
        scalafixHelp
      } else {
        val scalafixConf = scalafixConfig.value.map(_.toPath)
        val (shell, mainArgs0) = scalafixArgsFromShell(
          shell0,
          projectDepsExternal,
          scalafixResolvers.in(ThisBuild).value,
          projectDepsInternal
        )
        val mainArgs = mainArgs0
          .withConfig(jutil.Optional.ofNullable(scalafixConf.orNull))
          .withRules(shell.rules.asJava)
          .safeWithArgs(shell.extra)
        val rulesThatWillRun = mainArgs.safeRulesThatWillRun()
        val isSemantic = rulesThatWillRun.exists(_.kind().isSemantic)
        if (isSemantic) {
          val names = rulesThatWillRun.map(_.name())
          scalafixSemantic(names, mainArgs, shell, config)
        } else {
          scalafixSyntactic(mainArgs, shell, config)
        }
      }
    }
  private def scalafixHelp: Def.Initialize[Task[Unit]] =
    Def.task {
      scalafixArgs().withParsedArguments(List("--help").asJava).run()
      ()
    }

  private def scalafixSyntactic(
      mainArgs: ScalafixArguments,
      shellArgs: ShellArgs,
      config: Configuration
  ): Def.Initialize[Task[Unit]] = Def.task {
    runArgs(
      filesToFix(shellArgs, config).value,
      mainArgs,
      streams.value.log
    )
  }

  private def scalafixSemantic(
      ruleNames: Seq[String],
      mainArgs: ScalafixArguments,
      shellArgs: ShellArgs,
      config: Configuration
  ): Def.Initialize[Task[Unit]] = Def.taskDyn {
    val dependencies = allDependencies.value
    val files = filesToFix(shellArgs, config).value
    val withScalaArgs = mainArgs
      .withScalaVersion(scalaVersion.value)
      .withScalacOptions(scalacOptions.value.asJava)
    val errors = new SemanticRuleValidator(
      new SemanticdbNotFound(ruleNames, scalaVersion.value, sbtVersion.value)
    ).findErrors(files, dependencies, withScalaArgs)
    if (errors.isEmpty) {
      Def.task {
        val args = withScalaArgs.withClasspath(
          fullClasspath.value.map(_.data.toPath).asJava
        )
        runArgs(
          files,
          args,
          streams.value.log
        )
      }
    } else {
      Def.task {
        if (errors.length == 1) {
          throw new InvalidArgument(errors.head)
        } else {
          val message = errors.zipWithIndex
            .map { case (msg, i) => s"[E${i + 1}] $msg" }
            .mkString(s"${errors.length} errors\n", "\n", "")
          throw new InvalidArgument(message)
        }
      }
    }
  }

  private def runArgs(
      paths: Seq[Path],
      mainArgs: ScalafixArguments,
      logger: Logger
  ): Unit = {
    val finalArgs = mainArgs
      .withPaths(paths.asJava)

    if (paths.nonEmpty) {
      if (paths.lengthCompare(1) > 0) {
        logger.info(s"Running scalafix on ${paths.size} Scala sources")
      }

      val errors = finalArgs.run()
      if (errors.nonEmpty) {
        throw new ScalafixFailed(errors.toList)
      }
    } else {
      () // do nothing
    }
  }

  private def isScalaFile(file: File): Boolean = {
    val path = file.getPath
    path.endsWith(".scala") ||
    path.endsWith(".sbt")
  }
  private def filesToFix(
      shellArgs: ShellArgs,
      config: Configuration
  ): Def.Initialize[Task[Seq[Path]]] =
    Def.taskDyn {
      // Dynamic task to avoid redundantly computing `unmanagedSources.value`
      if (shellArgs.files.nonEmpty) {
        Def.task {
          shellArgs.files.map(file(_).toPath)
        }
      } else {
        Def.task {
          for {
            source <- unmanagedSources.in(config).in(scalafix).value
            if source.exists()
            if isScalaFile(source)
          } yield source.toPath
        }
      }
    }

  implicit class XtensionArgs(mainArgs: ScalafixArguments) {
    def safeRulesThatWillRun(): Seq[ScalafixRule] = {
      try mainArgs.rulesThatWillRun().asScala
      catch {
        case NonFatal(e) =>
          throw new InvalidArgument(e.getMessage)
      }
    }
    def safeWithArgs(args: Seq[String]): ScalafixArguments = {
      try mainArgs.withParsedArguments(args.asJava)
      catch {
        case NonFatal(e) =>
          throw new InvalidArgument(e.getMessage)
      }
    }
  }

  final class UninitializedError extends RuntimeException("uninitialized value")
}
