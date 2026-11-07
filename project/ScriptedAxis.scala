import sbt._
import sbt.ScriptedPlugin.autoImport.scriptedSbt

/** A scripted runner axis identifying the sbt version launched by the runner.
  */
final case class ScriptedAxis(sbtVersion: String, id: String)
    extends VirtualAxis.WeakAxis {

  override val idSuffix = id
  override val directorySuffix = s"scripted-$id"

  private def settings = Seq(
    scriptedSbt := sbtVersion
  )
}

object ScriptedAxis {
  implicit class ScriptedProjectMatrix(
      private val projectMatrix: ProjectMatrix
  ) {
    def jvmPlatformScripted(
        scalaVersion: String,
        scriptedSbtVersion: Seq[ScriptedAxis],
        settings: Seq[Setting[?]] = Nil
    ): ProjectMatrix =
      scriptedSbtVersion.foldLeft(projectMatrix) { (matrix, axis) =>
        matrix.jvmPlatform(
          scalaVersions = List(scalaVersion),
          axisValues = List(axis),
          settings = axis.settings ++ settings
        )
      }
  }
}
