package scalafix.internal.sbt

import java.io.File
import java.nio.file.Path

import sbt.*

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
  val resolvedScalacOptions = taskKey[Seq[String]]("scalacOptions with cache placeholders (e.g. ${CSR_CACHE}) resolved to absolute machine paths.")

  def resolvedScalacOptionsTask(config: ConfigKey): Def.Initialize[Task[Seq[String]]] =
    Def.task {
      (config / Keys.compile / resolvedScalacOptions).?.value match
        case Some(xs) => xs
        case _        => (config / Keys.compile / Keys.scalacOptions).value
    }
}
