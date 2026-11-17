package scalafix.internal.sbt

import java.io.File
import java.nio.file.Path

import scala.jdk.CollectionConverters.*

import sbt.*
import sbt.Keys.*

import org.apache.ivy.plugins.resolver.IBiblioResolver
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

  def ibiblioPatterns(
      raw: sbt.librarymanagement.RawRepository
  ): Option[sbt.librarymanagement.Patterns] =
    raw.resolver match {
      case resolver: IBiblioResolver =>
        def strings(values: java.util.List[?]): Vector[String] =
          Option(values)
            .map(_.asScala.toVector)
            .getOrElse(Vector.empty)
            .collect { case value: String =>
              value
            }

        Some(
          sbt.librarymanagement.Patterns(
            ivyPatterns = strings(resolver.getIvyPatterns),
            artifactPatterns = strings(resolver.getArtifactPatterns),
            isMavenCompatible = resolver.isM2compatible,
            descriptorOptional = !resolver.isUseMavenMetadata,
            skipConsistencyCheck = !resolver.isCheckconsistency
          )
        )
      case _ => None
    }

  implicit class DefOps(private val self: sbt.Def.type) extends AnyVal {
    def uncached[A](a: A): A = a
  }

  def resolvedScalacOptionsTask(
      task: TaskKey[?]
  ): Def.Initialize[Task[Seq[String]]] =
    task / scalacOptions
}
