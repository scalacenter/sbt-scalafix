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
  val resolvedScalacOptions: TaskKey[Seq[String]] = taskKey[Seq[String]](
    "scalacOptions with cache placeholders (e.g. ${CSR_CACHE}) resolved to absolute machine paths."
  )
  @transient
  val scalafixResolvedScalacOptions: TaskKey[Seq[String]] =
    taskKey[Seq[String]]("scalacOptions resolved for Scalafix")

  def resolvedScalacOptionsTask(
      config: ConfigKey
  ): Def.Initialize[Task[Seq[String]]] =
    Def.task {
      val options = (config / Keys.compile / Keys.scalacOptions).value
      val taskScopedResolvedOptionsAvailable =
        VersionNumber(sbtVersion.value).matchesSemVer(
          SemanticSelector(">=2.1.0-RC3")
        )
      if taskScopedResolvedOptionsAvailable then
        (config / Keys.compile / resolvedScalacOptions).?.value match
          case Some(xs) => xs
          case None =>
            resolveVirtualizedScalacOptions(options, Keys.rootPaths.value)
      else resolveVirtualizedScalacOptions(options, Keys.rootPaths.value)
    }

  // sbt 2.0's resolvedScalacOptions doesn't inherit task-scoped scalacOptions.
  // Keep the equivalent conversion here until sbt 2.1.0-RC3, which includes sbt/sbt#10030.
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
