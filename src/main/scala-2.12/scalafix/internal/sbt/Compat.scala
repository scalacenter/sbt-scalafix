package scalafix.internal.sbt

import java.io.File
import java.nio.file.Path

import sbt.Attributed
import sbt.Keys
import sbt.ModuleID

import xsbti.FileConverter

object Compat {

  def toPath(
      attributed: Attributed[File]
  )(implicit conv: FileConverter): Path =
    toFile(attributed).toPath()

  def toFile(
      attributed: Attributed[File]
  )(implicit conv: FileConverter): File = {
    val _ = conv // avoid unused warning
    attributed.data
  }

  def extractModuleID(
      attributed: Attributed[File]
  ): Option[ModuleID] =
    attributed.get(Keys.moduleID.key)
}
