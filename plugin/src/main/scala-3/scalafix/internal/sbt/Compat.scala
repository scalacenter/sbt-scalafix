package scalafix.internal.sbt

import java.io.File
import java.nio.file.Path

import sbt.*
import sbt.Keys.*

import xsbti.FileConverter
import xsbti.HashedVirtualFileRef

object Compat {

  def toPath(
      attributed: Attributed[HashedVirtualFileRef]
  )(using conv: FileConverter): Path =
    conv.toPath(attributed.data)

  def toFile(
      attributed: Attributed[HashedVirtualFileRef]
  )(using conv: FileConverter): File =
    toPath(attributed).toFile()

  def extractModuleID(
      attributed: Attributed[HashedVirtualFileRef]
  ): Option[ModuleID] =
    attributed
      .get(Keys.moduleIDStr)
      .map(Classpaths.moduleIdJsonKeyFormat.read)

  type DirectCredentials = sbt.librarymanagement.Credentials.DirectCredentials
  type FileCredentials = sbt.librarymanagement.Credentials.FileCredentials
  val Credentials = sbt.internal.librarymanagement.ivy.IvyCredentials

  @transient
  private val resolvedScalacOptions: TaskKey[Seq[String]] =
    taskKey[Seq[String]](
      "scalacOptions with cache placeholders (e.g. ${CSR_CACHE}) resolved to absolute machine paths."
    )

  def resolvedScalacOptionsTask(
      task: TaskKey[?]
  ): Def.Initialize[Task[Seq[String]]] =
    Def.taskDyn {
      val sbtKey = task / resolvedScalacOptions
      val sbtScope =
        Scope.replaceThis(resolvedScoped.value.scope)(sbtKey.scope)
      val sbtKeyDefined = settingsData.value
        .getDirect(Def.ScopedKey(sbtScope, sbtKey.key))
        .isDefined
      if sbtKeyDefined then Def.task(sbtKey.value)
      else
        // sbt < 2.0.7 does not define resolvedScalacOptions (sbt/sbt#9629), and sbt < 2.1.0-RC3
        // only defines it at the config level, ignoring task-scoped scalacOptions (sbt/sbt#10030)
        Def.task(
          resolveVirtualizedScalacOptions(
            (task / scalacOptions).value,
            rootPaths.value
          )
        )
    }

  def sbt2ScalafixTask(
      interface: ScalafixInterface,
      files: Seq[Path],
      sourceStamps: Seq[(Path, sbt.nio.FileStamp)],
      streams: TaskStreams,
      fileConverter: FileConverter,
      baseDirectory: Path,
      check: Boolean,
      run: () => Set[File],
      sbtVersion: String
  ): Def.Initialize[Task[Unit]] =
    Sbt2ScalafixCache.task(
      interface,
      files,
      sourceStamps,
      streams,
      fileConverter,
      baseDirectory,
      check,
      run,
      sbtVersion
    )

  // Copy of sbt.internal.Compiler.resolveVirtualizedScalacOptions (private[sbt], unchanged since 2.0.0)
  private def resolveVirtualizedScalacOptions(
      options: Seq[String],
      rootPaths: Map[String, Path]
  ): Seq[String] =
    def convertValue(value: String): String =
      rootPaths.find { case (key, _) => value.startsWith(s"$${$key}/") } match
        case Some((key, path)) =>
          path.resolve(value.stripPrefix(s"$${$key}/")).toString
        case None => value

    options.map(
      _.split(":")
        .map(_.split(",").map(convertValue).mkString(","))
        .mkString(":")
    )
}
