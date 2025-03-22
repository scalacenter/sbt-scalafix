package scalafix.internal.sbt

import java.io.File
import java.nio.file.Path

import sbt.Attributed
import sbt.Classpaths
import sbt.Keys
import sbt.ModuleID

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
}
