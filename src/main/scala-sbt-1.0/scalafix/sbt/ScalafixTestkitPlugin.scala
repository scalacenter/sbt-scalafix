package scalafix.sbt

import sbt.Keys._
import sbt._
import sbt.internal.ProjectMatrix
import sbt.plugins.JvmPlugin
import sbtprojectmatrix.ProjectMatrixPlugin.autoImport._

import java.io.File.pathSeparator

object ScalafixTestkitPlugin extends AutoPlugin {
  override def trigger: PluginTrigger = noTrigger
  override def requires: Plugins = JvmPlugin

  object autoImport {
    case class TestkitTargetAxis(scalaVersion: String)
        extends VirtualAxis.WeakAxis {
      private val scalaBinaryVersion =
        CrossVersion.binaryScalaVersion(scalaVersion)

      override val idSuffix = s"Target${scalaBinaryVersion.replace('.', '_')}"
      override val directorySuffix = s"target$scalaBinaryVersion"
    }

    object TestkitTargetAxis {

      private def targetScalaVersion(virtualAxes: Seq[VirtualAxis]): String =
        virtualAxes.collectFirst { case a: TestkitTargetAxis =>
          a.scalaVersion
        }.get

      def resolve[T](
          matrix: ProjectMatrix,
          key: TaskKey[T]
      ): Def.Initialize[Task[T]] =
        Def.taskDyn {
          val sv = targetScalaVersion(virtualAxes.value)
          val project = matrix.finder().apply(sv)
          Def.task((project / key).value)
        }

      def resolve[T](
          matrix: ProjectMatrix,
          key: SettingKey[T]
      ): Def.Initialize[T] =
        Def.settingDyn {
          val sv = targetScalaVersion(virtualAxes.value)
          val project = matrix.finder().apply(sv)
          Def.setting((project / key).value)
        }
    }

    val scalafixTestkitInputClasspath =
      taskKey[Classpath]("Classpath of input project")
    val scalafixTestkitInputScalacOptions =
      taskKey[Seq[String]](
        "Scalac compiler flags that were used to compile the input project"
      )
    val scalafixTestkitInputScalaVersion =
      settingKey[String](
        "Scala compiler version that was used to compile the input project"
      )
    val scalafixTestkitInputSourceDirectories =
      taskKey[Seq[File]]("Source directories of input project")
    val scalafixTestkitOutputSourceDirectories =
      taskKey[Seq[File]]("Source directories of output project")
  }
  import autoImport._

  override def buildSettings: Seq[Def.Setting[_]] =
    List(
      resolvers += Resolver.sonatypeRepo("snapshots")
    )

  override def projectSettings: Seq[Def.Setting[_]] =
    List(
      scalafixTestkitInputScalacOptions := scalacOptions.value,
      scalafixTestkitInputScalaVersion := scalaVersion.value,
      resourceGenerators.in(Test) += Def.task {
        val props = new java.util.Properties()
        val values = Map[String, Seq[File]](
          "sourceroot" ->
            List(baseDirectory.in(ThisBuild).value),
          "inputClasspath" ->
            scalafixTestkitInputClasspath.value.map(_.data),
          "inputSourceDirectories" ->
            scalafixTestkitInputSourceDirectories.value.distinct, // https://github.com/sbt/sbt/pull/6511
          "outputSourceDirectories" ->
            scalafixTestkitOutputSourceDirectories.value
        )
        values.foreach { case (key, files) =>
          props.put(
            key,
            files.iterator.filter(_.exists()).mkString(pathSeparator)
          )
        }
        props.put("scalaVersion", scalafixTestkitInputScalaVersion.value)
        props.put(
          "scalacOptions",
          scalafixTestkitInputScalacOptions.value.mkString("|")
        )
        val out =
          managedResourceDirectories.in(Test).value.head /
            "scalafix-testkit.properties"
        IO.write(props, "Input data for scalafix testkit", out)
        List(out)
      }
    )
}
