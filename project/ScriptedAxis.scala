import sbt._
import sbt.ScriptedPlugin.autoImport.scriptedSbt

/** A scripted runner axis identifying the sbt version launched by the runner.
  *
  * The id is inferred from the sbt version: 1.* -> 1, 2.y.* -> 2y
  */
final case class ScriptedAxis(sbtVersion: String) extends VirtualAxis.WeakAxis {

  private def id: String =
    val parts = sbtVersion.split('.')
    if (parts(0) == "1") "1"
    else parts.take(2).mkString("")

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
        scriptedSbtVersion: Seq[String],
        settings: Seq[Setting[?]] = Nil
    ): ProjectMatrix =
      scriptedSbtVersion.foldLeft(projectMatrix) { (matrix, version) =>
        val axis = ScriptedAxis(version)
        matrix.jvmPlatform(
          scalaVersions = List(scalaVersion),
          axisValues = List(axis),
          settings = axis.settings ++ settings
        )
      }
  }
}
