package scalafix.sbt

import java.io.PrintStream
import java.nio.file.{Path, Paths}

import com.geirsson.{coursiersmall => cs}
import coursierapi.Repository
import sbt.KeyRanks.Invisible
import sbt.Keys._
import sbt._
import sbt.internal.sbtscalafix.JLineAccess
import sbt.plugins.JvmPlugin
import scalafix.interfaces.ScalafixError
import scalafix.internal.sbt.Arg.ToolClasspath
import scalafix.internal.sbt._

import scala.collection.JavaConverters.collectionAsScalaIterableConverter
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
        "Run scalafix rule(s) in this project and configuration. " +
          "For example: scalafix RemoveUnused. " +
          "To run on test sources use test:scalafix or scalafixAll. " +
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

    import scala.language.implicitConversions
    @deprecated(
      "Usage of coursiersmall.Repository is deprecated, use coursierapi.Repository instead",
      "0.9.17"
    )
    implicit def coursiersmall2coursierapi(
        csRepository: cs.Repository
    ): Repository =
      csRepository match {
        case m: cs.Repository.Maven => coursierapi.MavenRepository.of(m.root)
        case i: cs.Repository.Ivy => coursierapi.IvyRepository.of(i.root)
        case cs.Repository.Ivy2Local => coursierapi.Repository.ivy2Local()
      }
    val scalafixResolvers: SettingKey[Seq[Repository]] =
      settingKey[Seq[Repository]](
        "Optional list of Maven/Ivy repositories to use for fetching custom rules."
      )
    val scalafixDependencies: SettingKey[Seq[ModuleID]] =
      settingKey[Seq[ModuleID]](
        "Optional list of custom rules to install from Maven Central. " +
          "This setting is read from the global scope so it only needs to be defined once in the build."
      )
    val scalafixScalaBinaryVersion: SettingKey[String] =
      settingKey[String](
        "The Scala binary version used for scalafix execution. Defaults to 2.12. " +
          s"Rules must be compiled against that binary version, or for advanced rules such as " +
          s"ExplicitResultTypes which have a full cross-version, against the corresponding full" +
          s"version that scalafix is built against."
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
      inConfig(config)(
        relaxScalacOptionsConfigSettings ++ Seq(
          scalafix := {
            // force detection of usage of `scalafixCaching` to workaround https://github.com/sbt/sbt/issues/5647
            val _ = scalafixCaching.?.value
            scalafixInputTask(config).evaluated
          },
          compile := Def.taskDyn {
            val oldCompile =
              compile.value // evaluated first, before the potential scalafix evaluation
            if (scalafixOnCompile.value)
              scalafix
                .toTask(" --triggered")
                .map(_ => oldCompile)
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

  private val scalafixInterfaceProvider: SettingKey[() => ScalafixInterface] =
    SettingKey(
      "scalafixInterfaceProvider",
      "Implementation detail - do not use",
      Invisible
    )

  private val scalafixCompletions: SettingKey[ScalafixCompletions] =
    SettingKey(
      "scalafixCompletions",
      "Implementation detail - do not use",
      Invisible
    )

  // Memoize ScalafixInterface instances initialized with a custom tool classpath across projects & configurations
  // during task execution, to amortize the classloading cost when invoking scalafix concurrently on many targets
  private val scalafixInterfaceCache
      : TaskKey[BlockingCache[ToolClasspath, ScalafixInterface]] =
    TaskKey(
      "scalafixInterfaceCache",
      "Implementation detail - do not use",
      Invisible
    )

  private lazy val cachingStyle = {
    val useLastModifiedCachingStyle =
      sys.props.get("sbt-scalafix.uselastmodified") == Some("true")
    if (useLastModifiedCachingStyle) FileInfo.lastModified
    else FileInfo.hash
  }

  override lazy val projectConfigurations: Seq[Configuration] =
    Seq(ScalafixConfig)

  override lazy val projectSettings: Seq[Def.Setting[_]] =
    Seq(Compile, Test).flatMap(c => inConfig(c)(scalafixConfigSettings(c))) ++
      inConfig(ScalafixConfig)(
        Defaults.configSettings :+ (sourcesInBase := false)
      ) ++
      Seq(
        ivyConfigurations += ScalafixConfig,
        scalafixAll := scalafixAllInputTask.evaluated
      )

  override lazy val globalSettings: Seq[Def.Setting[_]] = Seq(
    scalafixConfig := None, // let scalafix-cli try to infer $CWD/.scalafix.conf
    scalafixOnCompile := false,
    scalafixCaching := true,
    scalafixResolvers :=
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
        ),
    scalafixDependencies := Nil,
    commands += ScalafixEnable.command,
    scalafixInterfaceProvider := ScalafixInterface.fromToolClasspath(
      (ThisBuild / scalafixScalaBinaryVersion).value,
      scalafixDependencies = (ThisBuild / scalafixDependencies).value,
      scalafixCustomResolvers = (ThisBuild / scalafixResolvers).value
    ),
    scalafixCompletions := new ScalafixCompletions(
      workingDirectory = (ThisBuild / baseDirectory).value.toPath,
      // Unfortunately, local rules will not show up as completions in the parser, as that parser can only
      // depend on settings, while local rules classpath must be looked up via tasks
      loadedRules = () => scalafixInterfaceProvider.value().availableRules(),
      terminalWidth = Some(JLineAccess.terminalWidth)
    ),
    scalafixInterfaceCache := new BlockingCache[
      ToolClasspath,
      ScalafixInterface
    ],
    concurrentRestrictions += Tags.exclusiveGroup(Scalafix)
  )

  override def buildSettings: Seq[Def.Setting[_]] =
    Seq(
      scalafixScalaBinaryVersion := "2.12"
    )

  lazy val stdoutLogger = ConsoleLogger(System.out)

  private def scalafixArgsFromShell(
      shell: ShellArgs,
      scalafixInterface: () => ScalafixInterface,
      scalafixInterfaceCache: BlockingCache[ToolClasspath, ScalafixInterface],
      projectDepsExternal: Seq[ModuleID],
      baseDepsExternal: Seq[ModuleID],
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
    val projectDepsInternal0 = projectDepsInternal.filter {
      case directory if directory.isDirectory =>
        directory.**(AllPassFilter).get.exists(_.isFile)
      case file if file.isFile => true
      case _ => false
    }
    val customToolClasspath =
      (projectDepsInternal0 ++ projectDepsExternal ++ rulesDepsExternal).nonEmpty
    val interface =
      if (customToolClasspath) {
        val toolClasspath = ToolClasspath(
          projectDepsInternal0.map(_.toURI),
          baseDepsExternal ++ projectDepsExternal ++ rulesDepsExternal,
          baseResolvers
        )
        scalafixInterfaceCache.getOrElseUpdate(
          toolClasspath,
          // costly: triggers artifact resolution & classloader creation
          scalafixInterface().withArgs(toolClasspath)
        )
      } else
        // if there is nothing specific to the project or the invocation, reuse the default
        // interface which already has the baseDepsExternal loaded
        scalafixInterface()

    val newShell = shell.copy(rules = rules ++ parsed.map(_.ruleName))
    (newShell, interface)

  }

  private def scalafixAllInputTask(): Def.Initialize[InputTask[Unit]] =
    // workaround https://github.com/sbt/sbt/issues/3572 by invoking directly what Def.inputTaskDyn would via macro
    InputTask
      .createDyn(InputTask.initParserAsInput(scalafixCompletions(_.parser)))(
        Def.task(shellArgs =>
          scalafixAllTask(shellArgs, thisProject.value, resolvedScoped.value)
        )
      )

  private def scalafixAllTask(
      shellArgs: ShellArgs,
      project: ResolvedProject,
      scopedKey: ScopedKey[_]
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
    // workaround https://github.com/sbt/sbt/issues/3572 by invoking directly what Def.inputTaskDyn would via macro
    InputTask
      .createDyn(InputTask.initParserAsInput(scalafixCompletions(_.parser)))(
        Def.task(shellArgs => scalafixTask(shellArgs, config))
      )

  private def scalafixTask(
      shellArgs: ShellArgs,
      config: ConfigKey
  ): Def.Initialize[Task[Unit]] = {
    val task = Def.taskDyn {
      val errorLogger =
        new PrintStream(
          LoggingOutputStream(
            (config / scalafix / streams).value.log,
            Level.Error
          )
        )
      val projectDepsInternal = (ScalafixConfig / products).value ++
        (ScalafixConfig / internalDependencyClasspath).value.map(_.data)
      val projectDepsExternal =
        (ScalafixConfig / externalDependencyClasspath).value
          .flatMap(_.get(moduleID.key))

      if (shellArgs.rules.isEmpty && shellArgs.extra == List("--help")) {
        scalafixHelp
      } else {
        val scalafixConf = (config / scalafixConfig).value.map(_.toPath)
        val (shell, mainInterface0) = scalafixArgsFromShell(
          shellArgs,
          scalafixInterfaceProvider.value,
          scalafixInterfaceCache.value,
          projectDepsExternal,
          (ThisBuild / scalafixDependencies).value,
          (ThisBuild / scalafixResolvers).value,
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
    task.tag(Scalafix)
  }

  private def scalafixHelp: Def.Initialize[Task[Unit]] =
    Def.task {
      scalafixInterfaceProvider
        .value()
        .withArgs(Arg.ParsedArgs(List("--help")))
        .run()
      ()
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
            (config / dependencyClasspath).value.map(_.data.toPath) :+
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
        // sub-task of compile after which bytecode should not be modified
        task.dependsOn(config / manipulateBytecode)
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

      import sjsonnew._
      import sjsonnew.BasicJsonProtocol._

      val argWriter = new JsonWriter[Arg.CacheKey] {

        override final def write[J](
            obj: Arg.CacheKey,
            builder: Builder[J]
        ): Unit = stamp(obj)(builder)

        private def stamp[J](
            obj: Arg.CacheKey
        )(implicit builder: Builder[J]): Unit = obj match {
          case Arg.ToolClasspath(customURIs, customDependencies, _) =>
            val files = customURIs
              .map(uri => Paths.get(uri).toFile)
              .flatMap {
                case classDirectory if classDirectory.isDirectory =>
                  classDirectory.**(AllPassFilter).get
                case jar =>
                  Seq(jar)
              }
            write(files.map(stampFile).sorted)
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
              case "--tool-classpath" =>
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
          val confInArgs = interface.args
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

        private def write[J, A: JsonWriter](obj: A)(implicit
            builder: Builder[J]
        ): Unit = implicitly[JsonWriter[A]].write(obj, builder)
      }

      // we actually don't need to read anything back, see https://github.com/sbt/sbt/pull/5513
      implicit val argFormat = liftFormat(argWriter)

      def diffWithPreviousRuns[T](f: (Boolean, Set[File]) => T): T = {
        val tracker = Tracked.inputChanged(streams.cacheDirectory / "args") {
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
              )(targets) { changeReport: ChangeReport[File] =>
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

            val ruleTargetDiffs = interface.rulesThatWillRun
              .map(rule => diffTargets(rule.name) _)
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
    Def.taskDyn {
      // Dynamic task to avoid redundantly computing `unmanagedSources.value`
      if (shellArgs.files.nonEmpty) {
        Def.task {
          shellArgs.files.map(file(_).toPath)
        }
      } else {
        Def.task {
          for {
            source <- (config / scalafix / unmanagedSources).value
            if source.exists()
            if isScalaFile(source)
          } yield source.toPath
        }
      }
    }

  private[sbt] val relaxScalacOptionsConfigSettings: Seq[Def.Setting[_]] =
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
      },
      manipulateBytecode := {
        val analysis = manipulateBytecode.value
        if (!scalafixInvoked.value) analysis
        else {
          // prevent storage of the analysis with relaxed scalacOptions - despite not depending explicitly on compile,
          // it is being triggered for parent configs/projects through evaluation of dependencyClasspath (TrackAlways)
          // in the scope where scalafix is invoked
          analysis.withHasModified(false)
        }
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
    List("-Xfatal-warnings", "-Werror", "-Wconf.*").map(_.r.pattern)
}
