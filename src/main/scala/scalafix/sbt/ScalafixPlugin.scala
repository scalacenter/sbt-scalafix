package scalafix.sbt

import java.io.PrintStream
import java.nio.file.{Path, Paths}

import com.geirsson.coursiersmall.Repository
import sbt.KeyRanks.Invisible
import sbt.Keys._
import sbt._
import sbt.internal.sbtscalafix.{Compat, JLineAccess}
import sbt.plugins.JvmPlugin
import sbt.internal.sbtscalafix.Caching._
import scalafix.interfaces.ScalafixError
import scalafix.internal.sbt._

import scala.util.Try
import scala.util.control.NoStackTrace

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
    val scalafixCaching: SettingKey[Boolean] =
      settingKey[Boolean](
        "Cache scalafix invocations (off by default, still experimental)."
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
        scalafix := scalafixInputTask(config).evaluated,
        // In some cases (I haven't been able to understand when/why, but this also happens for bgRunMain while
        // fgRunMain is fine), there is no specific streams attached to InputTasks, so  we they end up sharing the
        // global streams, causing issues for cache storage. This does not happen for Tasks, so we define a dummy one
        // to acquire a distinct streams instance for each InputTask.
        scalafixDummyTask := (()),
        streams.in(scalafix) := streams.in(scalafixDummyTask).value
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

  private val scalafixDummyTask: TaskKey[Unit] =
    TaskKey(
      "scalafixDummyTask",
      "Implementation detail - do not use",
      Invisible
    )

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
    scalafixCaching := false,
    scalafixResolvers := ScalafixCoursier.defaultResolvers,
    scalafixDependencies := Nil,
    commands += ScalafixEnable.command
  )

  lazy val stdoutLogger = Compat.ConsoleLogger(System.out)
  private var workingDirectory = file("").getAbsoluteFile.toPath
  private var scalafixInterface: () => ScalafixInterface =
    () => throw new UninitializedError

  private def scalafixArgsFromShell(
      shell: ShellArgs,
      projectDepsExternal: Seq[ModuleID],
      baseResolvers: Seq[Repository],
      projectDepsInternal: Seq[File]
  ): (ShellArgs, ScalafixInterface) = {
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
    (newShell, interface)

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
        loadedRules = () => scalafixInterface().availableRules(),
        terminalWidth = Some(JLineAccess.terminalWidth)
      ).parser.parsed
      val errorLogger =
        new PrintStream(LoggingOutputStream(streams.value.log, Level.Error))
      val projectDepsInternal = products.in(ScalafixConfig).value ++
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
        val (shell, mainInterface0) = scalafixArgsFromShell(
          shell0,
          projectDepsExternal,
          scalafixResolvers.in(ThisBuild).value,
          projectDepsInternal
        )
        val maybeNoCache =
          if (shell.noCache || !scalafixCaching.value) Seq(Arg.NoCache) else Nil
        val mainInterface = mainInterface0
          .withArgs(maybeNoCache: _*)
          .withArgs(
            Arg.PrintStream(errorLogger),
            Arg.Config(scalafixConf),
            Arg.Rules(shell.rules),
            Arg.ParsedArgs(shell.extra)
          )
        val rulesThatWillRun = mainInterface.rulesThatWillRun()
        val isSemantic = rulesThatWillRun.exists(_.kind().isSemantic)
        if (isSemantic) {
          val names = rulesThatWillRun.map(_.name())
          scalafixSemantic(names, mainInterface, shell, config)
        } else {
          scalafixSyntactic(mainInterface, shell, config)
        }
      }
    }
  private def scalafixHelp: Def.Initialize[Task[Unit]] =
    Def.task {
      scalafixInterface().withArgs(Arg.ParsedArgs(List("--help"))).run()
      ()
    }

  private def scalafixSyntactic(
      mainInterface: ScalafixInterface,
      shellArgs: ShellArgs,
      config: Configuration
  ): Def.Initialize[Task[Unit]] =
    Def.task {
      val files = filesToFix(shellArgs, config).value
      runArgs(mainInterface.withArgs(Arg.Paths(files)), streams.value)
    }

  private def scalafixSemantic(
      ruleNames: Seq[String],
      mainArgs: ScalafixInterface,
      shellArgs: ShellArgs,
      config: Configuration
  ): Def.Initialize[Task[Unit]] =
    Def.taskDyn {
      val dependencies = allDependencies.value
      val files = filesToFix(shellArgs, config).value
      val withScalaInterface = mainArgs.withArgs(
        Arg.ScalaVersion(scalaVersion.value),
        Arg.ScalacOptions(scalacOptions.value)
      )
      val errors = new SemanticRuleValidator(
        new SemanticdbNotFound(ruleNames, scalaVersion.value, sbtVersion.value)
      ).findErrors(files, dependencies, withScalaInterface)
      if (errors.isEmpty) {
        Def.task {
          val semanticInterface = withScalaInterface.withArgs(
            Arg.Paths(files),
            Arg.Classpath(fullClasspath.value.map(_.data.toPath))
          )
          runArgs(semanticInterface, streams.value)
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
      interface: ScalafixInterface,
      streams: TaskStreams
  ): Unit = {
    val paths = interface.args.collect {
      case Arg.Paths(paths) => paths
    }.flatten
    if (paths.nonEmpty) {
      val cacheKeyArgs = interface.args.collect {
        case cacheKey: Arg.CacheKey => cacheKey
      }

      // used to signal that one of the argument cannot be reliably stamped
      object StampingImpossible extends RuntimeException with NoStackTrace

      implicit val stamper = new CacheKeysStamper {
        override protected def stamp: Arg.CacheKey => Unit = {
          case Arg.ToolClasspath(classLoader) =>
            val files = classLoader.getURLs
              .map(url => Paths.get(url.toURI).toFile)
              .flatMap {
                case classDirectory if classDirectory.isDirectory =>
                  classDirectory.**(AllPassFilter).get
                case jar =>
                  Seq(jar)
              }
            write(files.map(FileInfo.lastModified.apply))
          case Arg.Rules(rules) =>
            rules.foreach {
              case source
                  if source.startsWith("file:") ||
                    source.startsWith("github:") ||
                    source.startsWith("http:") ||
                    source.startsWith("https:") =>
                // don't bother stamping the source files
                throw StampingImpossible
              case _ =>
            }
            // don't stamp rules explicitly requested on the CLI but those which will actually run
            write(interface.rulesThatWillRun().map(_.name()))
          case Arg.Config(maybeFile) =>
            maybeFile match {
              case Some(path) =>
                write(FileInfo.lastModified(path.toFile))
              case None =>
                val defaultConfigFile = file(".scalafix.conf")
                write(FileInfo.lastModified(defaultConfigFile))
            }
          case Arg.ParsedArgs(args) =>
            val cacheKeys = args.filter {
              case "--check" | "--test" =>
                // CHECK & IN_PLACE can share the same cache
                false
              case "--syntactic" =>
                // this only affects rules selection, already accounted for in Arg.Rules
                false
              case "--stdout" =>
                // --stdout cannot be cached as we don't capture the output to replay it
                throw StampingImpossible
              case "--tool-classpath" =>
                // custom tool classpaths might contain directories for which we would need to stamp all files, so
                // just disable caching for now to keep it simple and to be safe
                throw StampingImpossible
              case _ => true
            }
            write(cacheKeys)
          case Arg.NoCache =>
            throw StampingImpossible
        }
      }

      def diffWithPreviousRun[T](f: (Boolean, ChangeReport[File]) => T): T = {
        val tracker = Tracked.inputChanged(streams.cacheDirectory / "inputs") {
          (inputChanged: Boolean, _: Seq[Arg.CacheKey]) =>
            val diffOutputs: Difference = Tracked.diffOutputs(
              streams.cacheDirectory / "outputs",
              lastModifiedStyle
            )
            diffOutputs(paths.map(_.toFile).toSet) {
              diffTargets: ChangeReport[File] =>
                f(inputChanged, diffTargets)
            }
        }
        Try(tracker(cacheKeyArgs)).recover {
          // in sbt 1.x, this is not necessary as any exception thrown during stamping is already silently ignored,
          // but having this here helps keeping code as common as possible
          // https://github.com/sbt/util/blob/v1.0.0/util-tracking/src/main/scala/sbt/util/Tracked.scala#L180
          case _ @StampingImpossible => f(true, new EmptyChangeReport())
        }.get
      }

      diffWithPreviousRun { (cacheKeyArgsChanged, diffTargets) =>
        val errors = if (cacheKeyArgsChanged) {
          streams.log.info(s"Running scalafix on ${paths.size} Scala sources")
          interface.run()
        } else {
          val dirtyTargets = diffTargets.modified -- diffTargets.removed
          if (dirtyTargets.nonEmpty) {
            streams.log.info(
              s"Running scalafix on ${dirtyTargets.size} Scala sources (incremental)"
            )
            interface
              .withArgs(Arg.Paths(dirtyTargets.map(_.toPath).toSeq))
              .run()
          } else {
            streams.log.debug(s"already ran on ${paths.length} files")
            Nil
          }
        }
        if (errors.nonEmpty) throw new ScalafixFailed(errors.toList)
        ()
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

  final class UninitializedError extends RuntimeException("uninitialized value")
}
