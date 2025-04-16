package scalafix.sbt

import java.io.PrintStream
import java.nio.file.Path

import scala.annotation.nowarn
import scala.jdk.CollectionConverters.*
import scala.util.Try
import scala.util.control.NoStackTrace

import sbt.*
import sbt.KeyRanks.Invisible
import sbt.Keys.*
import sbt.internal.sbtscalafix.JLineAccess
import sbt.internal.util.complete.Parser
import sbt.librarymanagement.ResolveException
import sbt.plugins.JvmPlugin

import coursierapi.Repository
import scalafix.interfaces.ScalafixError
import scalafix.interfaces.ScalafixMainCallback
import scalafix.internal.sbt.*
import xsbti.FileConverter

object ScalafixPlugin extends AutoPlugin {
  override def trigger: PluginTrigger = allRequirements
  override def requires: Plugins = JvmPlugin

  object autoImport {
    val Scalafix: Tags.Tag = Tags.Tag("scalafix")

    val ScalafixConfig: Configuration = config("scalafix")
      .describedAs("Dependencies required for rule execution.")

    val scalafix: InputKey[Unit] =
      inputKey[Unit](
        "Run scalafix rule(s) in this project and configuration. " +
          "For example: scalafix RemoveUnused. " +
          "To run on test sources use `Test / scalafix` or scalafixAll. " +
          "When invoked, prior compilation with -Xfatal-warnings relaxed will be triggered for semantic rules."
      )
    val scalafixAll: InputKey[Unit] =
      inputKey[Unit](
        "Run scalafix rule(s) in this project, for all configurations where scalafix is enabled. " +
          "Compile and Test are enabled by default, other configurations can be enabled via scalafixConfigSettings. " +
          "When invoked, prior compilation with -Xfatal-warnings relaxed will be triggered for semantic rules."
      )

    val scalafixOnCompile: SettingKey[Boolean] =
      settingKey[Boolean](
        "Run Scalafix rule(s) declared in .scalafix.conf on compilation and fail on lint errors." +
          "Default rules & rule configuration can be overridden using the `triggered` section." +
          "Off by default."
      )

    val scalafixCaching: SettingKey[Boolean] =
      settingKey[Boolean](
        "Make scalafix invocations incremental. On by default."
      )

    val scalafixResolvers: SettingKey[Seq[Repository]] =
      settingKey[Seq[Repository]](
        "Optional list of Maven/Ivy repositories to use for fetching custom rules. " +
          "Must be set in ThisBuild."
      )
    val scalafixDependencies: SettingKey[Seq[ModuleID]] =
      settingKey[Seq[ModuleID]](
        "Optional list of artifacts to resolve to run custom rules. " +
          "Can be set in ThisBuild or at project-level."
      )
    @deprecated(
      "scalafixScalaBinaryVersion now follows scalaVersion by default",
      "0.12.1"
    )
    val scalafixScalaBinaryVersion: SettingKey[String] =
      settingKey[String](
        "The Scala version used for scalafix execution. Must be set at project-level. " +
          "Custom rules must be compiled against that binary version. Defaults to the project's scalaVersion."
      )
    val scalafixConfig: SettingKey[Option[File]] =
      settingKey[Option[File]](
        "Optional location to .scalafix.conf file to specify which scalafix rules should run. " +
          "Defaults to the build base directory if a .scalafix.conf file exists."
      )

    val scalafixCallback: SettingKey[ScalafixMainCallback] =
      settingKey[ScalafixMainCallback](
        "The handler for the diagnostics emitted during scalafix execution. Must be set in ThisBuild. " +
          "Defaults to a wrapper around `sbt.Logger`."
      )

    val scalafixAllowDynamicFallback: SettingKey[Boolean] =
      settingKey[Boolean](
        "Control whether the latest version of scalafix may be downloaded instead of failing when " +
          "scalafix-interfaces is not explicitly provided. Off by default because non-deterministic."
      )

    lazy val scalafixSemanticdb: ModuleID =
      scalafixSemanticdb(BuildInfo.scalametaVersion)
    def scalafixSemanticdb(scalametaVersion: String): ModuleID =
      ("org.scalameta" % "semanticdb-scalac" % scalametaVersion)
        .cross(CrossVersion.full)

    def scalafixConfigSettings(config: Configuration): Seq[Def.Setting[?]] =
      inConfig(config)(
        relaxScalacOptionsConfigSettings ++ Seq(
          config / scalafix / skip := (config / scalafix / skip).value ||
            Seq("2.10", "2.11").contains(scalaBinaryVersion.value),
          scalafix := {
            // force evaluation of keys looked up in the same scope (config) within
            // dynamic tasks to workaround https://github.com/sbt/sbt/issues/5647
            val _ = Seq(
              scalafixCaching.?.value,
              scalafixConfig.?.value
            )
            scalafixInputTask(config).evaluated
          },
          compile := Def.taskDyn {
            val oldCompile =
              compile.value // evaluated first, before the potential scalafix evaluation
            if (scalafixOnCompile.value)
              Def.taskDyn {
                // evaluate scalafixInvoked only when scalafixOnCompile is set
                // to reduce risk of side effects on the build
                if (scalafixInvoked.value)
                  // don't trigger scalafix if it was explicitly invoked,
                  // as it would be not only inefficient, but incorrect since
                  // scalafix might run with different CLI args, potentially
                  // rewriting files while `--check` was used
                  Def.task(oldCompile)
                else
                  scalafix
                    .toTask(" --triggered")
                    .map(_ => oldCompile)
              }
            else Def.task(oldCompile)
          }.value,
          // In some cases (I haven't been able to understand when/why, but this also happens for bgRunMain while
          // fgRunMain is fine), there is no specific streams attached to InputTasks, so  we they end up sharing the
          // global streams, causing issues for cache storage. This does not happen for Tasks, so we define a dummy one
          // to acquire a distinct streams instance for each InputTask.
          scalafixDummyTask := (()),
          scalafix / streams := (scalafixDummyTask / streams).value
        )
      )
  }

  import autoImport._

  private val scalafixJGitCompletion: SettingKey[JGitCompletion] =
    SettingKey(
      "scalafixJGitCompletion",
      "Implementation detail - do not use",
      Invisible
    )

  private val scalafixDummyTask: TaskKey[Unit] =
    TaskKey(
      "scalafixDummyTask",
      "Implementation detail - do not use",
      Invisible
    )

  private def scalafixParser: Def.Initialize[Parser[ShellArgs]] =
    Def.setting {
      val allowedTargetFilesPrefixes =
        // only set in a config-level scope (i.e. scalafix, and not scalafixAll)
        (scalafix / unmanagedSourceDirectories).?.value.toSeq.flatten
          .map(_.toPath)
      new ScalafixCompletions(
        workingDirectory = (ThisBuild / baseDirectory).value.toPath,
        loadedRules = { () =>
          val scalafixInterface = ScalafixInterface(
            scalafixInterfaceCache.value,
            scalafixScalaBinaryVersion.value: @nowarn,
            toolClasspath = Arg.ToolClasspath(
              // Local rules classpath must be looked up via tasks so they can't appear in completions
              Nil,
              scalafixDependencies.value,
              // scalafixSbtResolversAsCoursierRepositories can't be
              // looked up here as it's a task
              (ThisBuild / scalafixResolvers).value
            ),
            logger = ScalafixInterface.defaultLogger,
            callback = (ThisBuild / scalafixCallback).value
          )
          // `scalafixDependencies` might be resolvable only from one of the
          // `scalafixSbtResolversAsCoursierRepositories` that we can't look up
          // here, as it's a task and we are in a settings context. Therefore
          // we fallback to an empty list of rules in case of resolution error
          // to keep providing completions for the rest.
          Try(scalafixInterface.availableRules()).getOrElse(Seq.empty)
        },
        terminalWidth = Some(JLineAccess.terminalWidth),
        allowedTargetFilesPrefixes = allowedTargetFilesPrefixes,
        jgitCompletion = scalafixJGitCompletion.value
      )
    }(_.parser)

  // Memoize ScalafixInterface instances across projects, configurations & invocations
  // to amortize the costs of artifact resolution & classloading.
  private val scalafixInterfaceCache: SettingKey[ScalafixInterface.Cache] =
    SettingKey(
      "scalafixInterfaceCache",
      "Implementation detail - do not use",
      Invisible
    )

  private val scalafixSbtResolversAsCoursierRepositories
      : TaskKey[Seq[Repository]] = TaskKey(
    "scalafixSbtResolversAsCoursierRepositories",
    "Implementation detail - do not use",
    Invisible
  )

  private lazy val cachingStyle = {
    val useLastModifiedCachingStyle =
      sys.props.get("sbt-scalafix.uselastmodified") == Some("true")
    if (useLastModifiedCachingStyle) FileInfo.lastModified
    else FileInfo.hash
  }

  private lazy val defaultScalafixResolvers =
    // Repository.defaults() defaults to Repository.ivy2Local() and Repository.central(). These can be overridden per
    // env variable, e.g., export COURSIER_REPOSITORIES="ivy2Local|central|sonatype:releases|jitpack|https://corporate.com/repo".
    // See https://github.com/coursier/coursier/blob/master/modules/coursier/jvm/src/main/scala/coursier/PlatformResolve.scala#L19-L68
    // and https://get-coursier.io/docs/other-repositories for more details.
    // Also see src/sbt-test/sbt-scalafix/scalafixResolvers/test for a scripted test preserving this behavior.
    Repository.defaults().asScala.toSeq ++
      Seq(
        coursierapi.MavenRepository.of(
          "https://oss.sonatype.org/content/repositories/public"
        )
      )

  override lazy val projectConfigurations: Seq[Configuration] =
    Seq(ScalafixConfig)

  override lazy val projectSettings: Seq[Def.Setting[?]] = Def.settings(
    Seq(Compile, Test).flatMap(c => inConfig(c)(scalafixConfigSettings(c))),
    inConfig(ScalafixConfig)(
      Def.settings(
        Defaults.configSettings,
        sourcesInBase := false,
        bspEnabled := false
      )
    ),
    update := {
      object SemanticdbScalac {
        def unapply(id: ModuleID): Option[String] =
          if (
            id.organization == scalafixSemanticdb.organization &&
            id.name.startsWith(scalafixSemanticdb.name) &&
            id.revision == BuildInfo.scalametaVersion
          ) Some(id.revision)
          else None
      }

      update.result.value.toEither match {
        case Right(v: UpdateReport) => v
        case Left(inc: Incomplete) =>
          Incomplete.allExceptions(inc).toList match {
            case (resolveException: ResolveException) :: Nil =>
              resolveException.failed.headOption match {
                case Some(SemanticdbScalac(rev)) =>
                  val scalaV = scalaVersion.value
                  val msg =
                    s"The SemanticDB scalac plugin version ${rev} set up " +
                      "via `semanticdbVersion` does follow the version recommended " +
                      "for Scalafix, but is not supported for the given Scala " +
                      s"version ${scalaV}. Please consider upgrading to a more recent version " +
                      "of \"ch.epfl.scala\" % \"scalafix-interfaces\" and/or Scala, or " +
                      "uninstalling sbt-scalafix."
                  throw inc.copy(message = Some(msg))
                case _ =>
              }
            case _ =>
          }
          throw inc
      }
    },
    ivyConfigurations += ScalafixConfig,
    scalafixAll := scalafixAllInputTask().evaluated,
    (scalafixScalaBinaryVersion := scalaVersion.value
      .split('.')
      .take(2)
      .mkString(".")): @nowarn
  )

  override lazy val globalSettings: Seq[Def.Setting[?]] = Seq(
    scalafixCallback := new ScalafixLogger(ScalafixInterface.defaultLogger),
    scalafixConfig := None, // let scalafix-cli try to infer $CWD/.scalafix.conf
    scalafixOnCompile := false,
    scalafixCaching := true,
    scalafixResolvers := defaultScalafixResolvers,
    scalafixDependencies := Nil,
    scalafixAllowDynamicFallback := false,
    commands += ScalafixEnable.command,
    scalafixInterfaceCache := new BlockingCache,
    concurrentRestrictions += Tags.exclusiveGroup(Scalafix),
    // sbt 2.0.0-M4 rightfully detects the default of semanticdbTargetRoot unused
    // as we don't inject SemanticdbPlugin.configurationSettings into the custom config
    excludeLintKeys += (ScalafixConfig / semanticdbTargetRoot)
  )

  override def buildSettings: Seq[Def.Setting[?]] =
    Seq(
      scalafixSbtResolversAsCoursierRepositories := {
        val logger = streams.value.log

        // mimics https://github.com/sbt/librarymanagement/blob/v1.10.0/ivy/src/main/scala/sbt/librarymanagement/ivy/Credentials.scala#L23-L28
        val asDirectCredentials = credentials.value.flatMap {
          _ match {
            case dc: DirectCredentials =>
              Some(dc)
            case fc: FileCredentials =>
              Credentials.loadCredentials(fc.path) match {
                case Left(err) =>
                  logger.warn(err)
                  None
                case Right(dc) =>
                  Some(dc)
              }
          }
        }

        val credentialsByHost =
          asDirectCredentials.map { dc =>
            dc.host -> coursierapi.Credentials.of(
              dc.userName,
              dc.passwd
            )
          }.toMap

        resolvers.value.flatMap { resolver =>
          CoursierRepoResolvers.repository(
            resolver,
            logger,
            credentialsByHost
          )
        }
      },
      scalafixJGitCompletion := new JGitCompletion(baseDirectory.value.toPath)
    )

  lazy val stdoutLogger: ConsoleLogger = ConsoleLogger(System.out)

  private def scalafixArgsFromShell(
      shell: ShellArgs,
      projectDepsExternal: Seq[ModuleID],
      projectDepsInternal: Seq[File],
      projectScalaMajorMinorVersion: String,
      projectScalafixDependencies: Seq[ModuleID],
      buildAllResolvers: Seq[Repository],
      buildScalafixMainCallback: ScalafixMainCallback,
      buildScalafixInterfaceCache: ScalafixInterface.Cache
  ): (ShellArgs, ScalafixInterface) = {
    val (dependencyRules, rules) =
      shell.rules.partition(_.startsWith("dependency:"))
    val parsed = dependencyRules.map {
      case DependencyRule.Binary(ruleName, org, art, ver) =>
        DependencyRule(ruleName, org %% art % ver)
      case DependencyRule.Full(ruleName, org, art, ver) =>
        DependencyRule(ruleName, (org %% art % ver).cross(CrossVersion.full))
      case els =>
        stdoutLogger.error(
          s"""|Invalid rule:    $els
            |Expected format: ${DependencyRule.format}
            |""".stripMargin
        )
        throw new ScalafixFailed(List(ScalafixError.CommandLineError))
    }
    val invocationDepsExternal = parsed.map(_.dependency)
    val projectDepsInternal0 = projectDepsInternal.filter {
      case directory if directory.isDirectory =>
        directory.**(AllPassFilter).get().exists(_.isFile)
      case file if file.isFile => true
      case _ => false
    }

    val toolClasspath = Arg.ToolClasspath(
      projectDepsInternal0.map(_.toURI),
      projectScalafixDependencies ++ projectDepsExternal ++ invocationDepsExternal,
      buildAllResolvers
    )

    val interface = ScalafixInterface(
      cache = buildScalafixInterfaceCache,
      scalafixScalaMajorMinorVersion = projectScalaMajorMinorVersion,
      toolClasspath = toolClasspath,
      logger = ScalafixInterface.defaultLogger,
      callback = buildScalafixMainCallback
    )

    val newShell = shell.copy(rules = rules ++ parsed.map(_.ruleName))
    (newShell, interface)

  }

  private def scalafixAllInputTask(): Def.Initialize[InputTask[Unit]] =
    Def.inputTaskDyn {
      scalafixAllTask(
        scalafixParser.parsed,
        thisProject.value,
        resolvedScoped.value
      )
    }

  private def scalafixAllTask(
      shellArgs: ShellArgs,
      project: ResolvedProject,
      scopedKey: ScopedKey[?]
  ): Def.Initialize[Task[Unit]] =
    Def.taskDyn {
      val configsWithScalafixInputKey = project.settings
        .map(_.key)
        .filter(_.key == scalafix.key)
        .flatMap(_.scope.config.toOption)
        .distinct

      // To avoid seeing 2 concurrent scalafixAll tasks for a given project in supershell, this renames them
      // to match the underlying configuration-scoped scalafix tasks
      def updateName[T](task: Task[T], config: ConfigKey): Task[T] =
        task.named(
          Scope.display(
            scopedKey.scope.copy(config = Select(config)),
            scalafix.key.label,
            {
              case ProjectRef(_, id) => s"$id /"
              case ref => s"${ref.toString} /"
            }
          )
        )

      configsWithScalafixInputKey
        .map { config =>
          scalafixTask(shellArgs, config)(task => updateName(task, config))
        }
        .joinWith(_.join.map(_ => ()))
    }

  private def scalafixInputTask(
      config: Configuration
  ): Def.Initialize[InputTask[Unit]] =
    Def.inputTaskDyn {
      scalafixTask(scalafixParser.parsed, config)
    }

  private def scalafixTask(
      shellArgs: ShellArgs,
      config: ConfigKey
  ): Def.Initialize[Task[Unit]] = {
    val task = Def.taskDyn {
      if (!scalafixAllowDynamicFallback.value) {
        // force scalafix properties to be loaded, to trigger an actionnable error instead
        // of letting the built-in scalafix-interfaces fallback to the latest scalafix version
        BuildInfo.scalafixVersion
      }

      implicit val conv: FileConverter = fileConverter.value

      val errorLogger =
        new PrintStream(
          LoggingOutputStream(
            (config / scalafix / streams).value.log,
            Level.Error
          )
        )
      val projectDepsInternal = ((ScalafixConfig / products).value ++
        (ScalafixConfig / internalDependencyClasspath).value
          .map(Compat.toFile))
      val projectDepsExternal =
        (ScalafixConfig / externalDependencyClasspath).value
          .flatMap(Compat.extractModuleID)

      val allResolvers =
        ((ThisBuild / scalafixResolvers).value ++ (ThisBuild / scalafixSbtResolversAsCoursierRepositories).value).distinct
      val (shell, baseInterface) = scalafixArgsFromShell(
        shellArgs,
        projectDepsExternal,
        projectDepsInternal,
        scalafixScalaBinaryVersion.value: @nowarn,
        scalafixDependencies.value,
        allResolvers,
        (ThisBuild / scalafixCallback).value,
        scalafixInterfaceCache.value
      )

      val informationalInvocation = shellArgs.extra
        .intersect(List("--help", "-h", "--version", "-v"))
        .nonEmpty

      if (informationalInvocation) {
        Def.task[Unit](
          baseInterface.withArgs(Arg.ParsedArgs(shell.extra)).run()
        )
      } else {
        val scalafixConf = (config / scalafixConfig).value.map(_.toPath)
        val maybeNoCache =
          if (shell.noCache || !scalafixCaching.value) Seq(Arg.NoCache) else Nil
        val mainInterface = baseInterface
          .withArgs(maybeNoCache*)
          .withArgs(
            Arg.PrintStream(errorLogger),
            Arg.Config(scalafixConf),
            Arg.Rules(shell.rules),
            Arg.ScalaVersion(scalaVersion.value),
            Arg.ScalacOptions((config / compile / scalacOptions).value),
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
    Def.taskDyn {
      if (!(config / scalafix / skip).value)
        task.tag(Scalafix)
      else
        Def.task {
          (config / scalafix / streams).value.log.info("Skipping scalafix")
        }
    }
  }

  private def scalafixSyntactic(
      mainInterface: ScalafixInterface,
      shellArgs: ShellArgs,
      config: ConfigKey
  ): Def.Initialize[Task[Unit]] =
    Def.task {
      val files = filesToFix(shellArgs, config).value
      runArgs(
        mainInterface.withArgs(Arg.Paths(files)),
        (config / scalafix / streams).value
      )
    }

  private def scalafixSemantic(
      ruleNames: Seq[String],
      mainArgs: ScalafixInterface,
      shellArgs: ShellArgs,
      config: ConfigKey
  ): Def.Initialize[Task[Unit]] =
    Def.taskDyn {
      implicit val conv: FileConverter = fileConverter.value

      val dependencies = (config / allDependencies).value
      val files = filesToFix(shellArgs, config).value
      val scalacOpts = (config / compile / scalacOptions).value
      val errors = new SemanticRuleValidator(
        new SemanticdbNotFound(ruleNames, scalaVersion.value)
      ).findErrors(files, dependencies, scalacOpts, mainArgs)
      if (errors.isEmpty) {
        val task = Def.task {
          // don't use fullClasspath as it results in a cyclic dependency via compile when scalafixOnCompile := true
          val classpath =
            (config / dependencyClasspath).value.map(Compat.toPath) :+
              (config / classDirectory).value.toPath
          val semanticInterface = mainArgs.withArgs(
            Arg.Paths(files),
            Arg.Classpath(classpath)
          )
          runArgs(
            semanticInterface,
            (config / scalafix / streams).value
          )
        }
        // we should not and cannot (circular dependency) trigger compilation
        // if this is an non-explicit invocation - we use the presence of a
        // flag rather than checking !scalafixInvoked, in order to trigger
        // compile in case scalafix is wrapped in another task that
        // scalafixInvoked does not know about
        if (shellArgs.extra.contains("--triggered")) task
        else task.dependsOn(config / compile)
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
    val paths = interface.args.collect { case Arg.Paths(paths) =>
      paths
    }.flatten
    if (paths.nonEmpty) {
      val cacheKeyArgs = interface.args.collect { case cacheKey: Arg.CacheKey =>
        cacheKey
      }

      // used to signal that one of the argument cannot be reliably stamped
      object StampingImpossible extends RuntimeException with NoStackTrace

      import sjsonnew.BasicJsonProtocol._

      val argWriter = new sjsonnew.JsonWriter[Arg.CacheKey] {

        override final def write[J](
            obj: Arg.CacheKey,
            builder: sjsonnew.Builder[J]
        ): Unit = stamp(obj)(builder)

        private def stamp[J](
            obj: Arg.CacheKey
        )(implicit builder: sjsonnew.Builder[J]): Unit = obj match {
          case toolClasspath @ Arg.ToolClasspath(_, customDependencies, _) =>
            write(toolClasspath.mutableFiles.map(stampFile).sorted)
            write(customDependencies.map(_.toString).sorted)
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
          // don't stamp rules as inputs: outputs are stamped by rule
          case Arg.Config(maybeFile) =>
            maybeFile match {
              case Some(path) =>
                write(stampFile(path.toFile))
              case None =>
                val defaultConfigFile = file(".scalafix.conf")
                write(stampFile(defaultConfigFile))
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
              case tcp if tcp.startsWith("--tool-classpath") =>
                // custom tool classpaths might contain directories for which we would need to stamp all files, so
                // just disable caching for now to keep it simple and to be safe
                throw StampingImpossible
              case "--triggered" =>
                // If there is a triggered section in the config file, --triggered flag should be accounted.
                // If not, --triggered can share the same cache.
                checkIfTriggeredSectionExists
              case _ => true
            }
            write(cacheKeys)
          case Arg.NoCache =>
            throw StampingImpossible
        }

        private lazy val checkIfTriggeredSectionExists: Boolean = {
          val confInArgs: Option[Path] = interface.args
            .collect { case Arg.Config(conf) => conf }
            .flatten
            .lastOption
          val configFile = confInArgs.fold(file(".scalafix.conf"))(_.toFile())
          if (configFile.exists()) {
            val lines = IO.readLines(configFile)
            lines.exists(_.trim.startsWith("triggered"))
          } else false
        }

        private def stampFile(file: File): String = {
          // ensure the file exists and is not a directory
          if (file.isFile)
            Hash.toHex(Hash(file))
          else
            ""
        }

        private def write[J, A](obj: A)(implicit
            writer: sjsonnew.JsonWriter[A],
            builder: sjsonnew.Builder[J]
        ): Unit = writer.write(obj, builder)
      }

      // implicit conversion of collection is only available on JsonFormat
      implicit val argFormat: sjsonnew.JsonFormat[Arg.CacheKey] =
        liftFormat(argWriter)

      def diffWithPreviousRuns[T](f: (Boolean, Set[File]) => T): T = {
        val tracker = Tracked.inputChangedW(streams.cacheDirectory / "args") {
          (argsChanged: Boolean, _: Seq[Arg.CacheKey]) =>
            val targets = paths.map(_.toFile).toSet

            // Stamp targets before execution of the provided function, and persist
            // the result only on successful execution of the provided function
            def diffTargets(
                rule: String
            )(doForStaleTargets: Set[File] => T): T =
              Tracked.diffInputs(
                streams.cacheDirectory / "targets-by-rule" / rule,
                cachingStyle
              )(targets) { (changeReport: ChangeReport[File]) =>
                doForStaleTargets(changeReport.modified -- changeReport.removed)
              }

            // Execute the callback function against the accumulated files that cache-miss for at least one rule
            def accumulateAndRunForStaleTargets(
                ruleTargetsDiffs: List[(Set[File] => T) => T],
                acc: Set[File] = Set.empty
            ): T =
              ruleTargetsDiffs match {
                case Nil => f(argsChanged, acc)
                case targetsDiff :: tail =>
                  targetsDiff { ruleStaleTargets =>
                    // This cannot be @tailrec as it must nest function calls, so that nothing is persisted
                    // in case of exception during evaluation of the one and only effect (the terminal callback
                    // above). Stack depth is limited to the number of requested rules to run, so it should
                    // be fine...
                    accumulateAndRunForStaleTargets(
                      tail,
                      acc ++ ruleStaleTargets
                    )
                  }
              }

            val ruleTargetDiffs = interface
              .rulesThatWillRun()
              .map(rule => diffTargets(rule.name)(_))
              .toList
            accumulateAndRunForStaleTargets(ruleTargetDiffs)
        }
        tracker(cacheKeyArgs)
      }

      diffWithPreviousRuns { (cacheKeyArgsChanged, staleTargets) =>
        val errors = if (cacheKeyArgsChanged) {
          streams.log.info(s"Running scalafix on ${paths.size} Scala sources")
          interface.run()
        } else {
          if (staleTargets.nonEmpty) {
            streams.log.info(
              s"Running scalafix on ${staleTargets.size} Scala sources (incremental)"
            )
            interface
              .withArgs(Arg.Paths(staleTargets.map(_.toPath).toSeq))
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
      config: ConfigKey
  ): Def.Initialize[Task[Seq[Path]]] =
    Def.taskIf {
      if (shellArgs.files.nonEmpty) shellArgs.files.map(file(_).toPath)
      else
        for {
          source <- (config / scalafix / unmanagedSources).value
          if source.exists()
          if isScalaFile(source)
        } yield source.toPath
    }

  private[sbt] val relaxScalacOptionsConfigSettings: Seq[Def.Setting[?]] =
    Seq(
      compile / scalacOptions := {
        val options = (compile / scalacOptions).value
        if (!scalafixInvoked.value) options
        else
          options.filterNot { option =>
            scalacOptionsToRelax.exists(_.matcher(option).matches)
          }
      },
      incOptions := {
        val options = incOptions.value
        if (!scalafixInvoked.value) options
        else
          // maximize chance to get a zinc cache hit when running scalafix, even though we have
          // potentially added/removed scalacOptions for that specific invocation
          options.withIgnoredScalacOptions(
            options.ignoredScalacOptions() ++
              scalacOptionsToRelax.map(_.pattern())
          )
      }
    )

  private def scalafixInvoked: Def.Initialize[Task[Boolean]] =
    Def.task {
      val (scalafixKeys, otherKeys) = executionRoots.value.partition { root =>
        Seq(
          scalafix.key,
          scalafixAll.key
        ).contains(root.key)
      }
      if (scalafixKeys.nonEmpty && otherKeys.nonEmpty) {
        // Fail hard if we detect other concurrent tasks requested, as `scalafixInvoked` is used to
        // conditionnally, transiently alter tasks transitively triggered by scalafix, so these tasks
        // should not see run with that altered behavior as it could cause compilation/packaging to
        // succeed even though the build requests fatal warnings and there were warnings detected.
        throw new InvalidArgument(
          "Scalafix cannot be invoked concurrently with other tasks"
        )
      }
      scalafixKeys.nonEmpty
    }

  private val scalacOptionsToRelax =
    List("-Xfatal-warnings", "-Werror", "-Wconf.*:(e|error)").map(_.r.pattern)
}
