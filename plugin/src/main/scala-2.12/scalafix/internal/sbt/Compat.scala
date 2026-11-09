package scalafix.internal.sbt

import java.io.File
import java.nio.file.Path

import sbt.*
import sbt.Keys.*

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

  type DirectCredentials = sbt.DirectCredentials
  type FileCredentials = sbt.FileCredentials
  val Credentials = sbt.Credentials

  implicit class DefOps(private val self: sbt.Def.type) extends AnyVal {
    def uncached[A](a: A): A = a
  }

  def resolvedScalacOptionsTask(
      task: TaskKey[?]
  ): Def.Initialize[Task[Seq[String]]] =
    task / scalacOptions

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
  ): Def.Initialize[Task[Unit]] = {
    val _ = (
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
    Def.task { () }
  }
}
