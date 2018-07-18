package scalafix.sbt

import scalafix.Versions
import sbt.File
import sbt.Keys._
import sbt._
import sbt.complete.Parser
import sbt.plugins.JvmPlugin
import scalafix.internal.sbt.ScalafixCompletions
import sbt.Def

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
    val scalafixConfig: SettingKey[Option[File]] =
      settingKey[Option[File]](
        "Optional location to .scalafix.conf file to specify which scalafix rules should run. " +
          "Defaults to the build base directory if a .scalafix.conf file exists."
      )
    val scalafixSemanticdbVersion: SettingKey[String] = settingKey[String](
      s"Which version of semanticdb to use. Default is ${Versions.scalameta}."
    )
    val scalafixSemanticdb: ModuleID =
      scalafixSemanticdb(Versions.scalameta)
    def scalafixSemanticdb(scalametaVersion: String): ModuleID =
      "org.scalameta" % "semanticdb-scalac" % scalametaVersion cross CrossVersion.full
    val scalafixVerbose: SettingKey[Boolean] =
      settingKey[Boolean]("pass --verbose to scalafix")

    lazy val scalafixConfigSettings: Seq[Def.Setting[_]] = Seq(
      scalafix := scalafixTaskImpl(
        scalafixParserCompat,
        compat = true,
        Seq("--format", "sbt")
      ).tag(Scalafix).evaluated,
      scalafixTest := scalafixTaskImpl(
        scalafixParserCompat,
        compat = true,
        Seq("--test", "--format", "sbt")
      ).tag(Scalafix).evaluated,
      scalafixCli := scalafixTaskImpl(
        scalafixParser,
        compat = false,
        Seq("--format", "sbt")
      ).tag(Scalafix).evaluated,
      scalafixAutoSuppressLinterErrors := scalafixTaskImpl(
        scalafixParser,
        compat = true,
        Seq("--auto-suppress-linter-errors", "--format", "sbt")
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

  override def projectSettings: Seq[Def.Setting[_]] =
    Seq(Compile, Test).flatMap(inConfig(_)(scalafixConfigSettings))

  override def globalSettings: Seq[Def.Setting[_]] = Seq(
    scalafixConfig := Option(file(".scalafix.conf")).filter(_.isFile),
    scalafixVerbose := false,
    commands += ScalafixEnable.command,
    sbtfix := sbtfixImpl(compat = true).evaluated,
    sbtfixTest := sbtfixImpl(compat = true, extraOptions = Seq("--test")).evaluated,
    aggregate.in(sbtfix) := false,
    aggregate.in(sbtfixTest) := false,
    scalafixSemanticdbVersion := Versions.scalameta
  )

  private def sbtfixImpl(compat: Boolean, extraOptions: Seq[String] = Seq()) = {
    Def.inputTaskDyn {
      val baseDir = baseDirectory.in(ThisBuild).value
      val sbtDir: File = baseDir./("project")
      val sbtFiles = baseDir.*("*.sbt").get
      scalafixTaskImpl(
        scalafixParserCompat.parsed,
        compat,
        extraOptions,
        sbtDir +: sbtFiles,
        "sbt-build",
        streams.value
      )
    }
  }

  // hack to avoid illegal dynamic reference, can't figure out how to do
  // scalafixParser(baseDirectory.in(ThisBuild).value).parsed
  private def workingDirectory = file(sys.props("user.dir"))

  private val scalafixParser =
    ScalafixCompletions.parser(workingDirectory.toPath, compat = false)
  private val scalafixParserCompat =
    ScalafixCompletions.parser(workingDirectory.toPath, compat = true)

  def scalafixTaskImpl(
      parser: Parser[Seq[String]],
      compat: Boolean,
      extraOptions: Seq[String] = Seq()
  ): Def.Initialize[InputTask[Unit]] =
    Def.inputTaskDyn {
      scalafixTaskImpl(parser.parsed, compat, extraOptions)
    }

  def scalafixTaskImpl(
      inputArgs: Seq[String],
      compat: Boolean,
      extraOptions: Seq[String]
  ): Def.Initialize[Task[Unit]] =
    Def.taskDyn {
      compile.value // trigger compilation
      val scalafixClasspath =
        classDirectory.value +:
          dependencyClasspath.value.map(_.data)
      val sourcesToFix = for {
        source <- unmanagedSources.in(scalafix).value
        if source.exists()
        if canFix(source)
      } yield source
      val options: Seq[String] = List(
        "--classpath",
        scalafixClasspath.mkString(java.io.File.pathSeparator)
      ) ++ extraOptions
      scalafixTaskImpl(
        inputArgs,
        compat,
        options,
        sourcesToFix,
        thisProject.value.id,
        streams.value
      )
    }

  def scalafixTaskImpl(
      inputArgs: Seq[String],
      compat: Boolean,
      options: Seq[String],
      files: Seq[File],
      projectId: String,
      streams: TaskStreams
  ): Def.Initialize[Task[Unit]] = {
    if (files.isEmpty) Def.task(())
    else {
      Def.task {
        val args = Array.newBuilder[String]

        if (scalafixVerbose.value) {
          args += "--verbose"
        }

        scalafixConfig.value match {
          case Some(x) =>
            args += (
              "--config",
              x.getAbsolutePath
            )
          case _ =>
        }

        if (compat && inputArgs.nonEmpty) {
          args += "--rules"
        }
        args ++= inputArgs
        args ++= options

        val nonBaseArgs = args.result().mkString(" ")
        if (scalafixVerbose.value) {
          streams.log.info(s"Running scalafix $nonBaseArgs")
        } else if (files.lengthCompare(1) > 0) {
          streams.log.info(s"Running scalafix on ${files.size} Scala sources")
        }

        args += "--no-sys-exit"

        args ++= files.iterator.map(_.getAbsolutePath)

        _root_.scalafix.v1.Main.main(args.result())
      }
    }
  }

  private def canFix(file: File): Boolean = {
    val path = file.getPath
    path.endsWith(".scala") ||
    path.endsWith(".sbt")
  }
}
