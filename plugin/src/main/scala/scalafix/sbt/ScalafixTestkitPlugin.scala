package scalafix.sbt

import java.io.File.pathSeparator

import sbt.*
import sbt.Keys.*
import sbt.plugins.JvmPlugin

import scalafix.internal.sbt.Compat
import xsbti.FileConverter

object ScalafixTestkitPlugin extends AutoPlugin {
  override def trigger: PluginTrigger = noTrigger
  override def requires: Plugins = JvmPlugin

  object autoImport {
    val scalafixTestkitInputClasspath: TaskKey[Classpath] =
      taskKey[Classpath]("Classpath of input project")
    val scalafixTestkitInputScalacOptions: TaskKey[Seq[String]] =
      taskKey[Seq[String]](
        "Scalac compiler flags that were used to compile the input project"
      )
    val scalafixTestkitInputScalaVersion: SettingKey[String] =
      settingKey[String](
        "Scala compiler version that was used to compile the input project"
      )
    val scalafixTestkitInputSourceDirectories: TaskKey[Seq[File]] =
      taskKey[Seq[File]]("Source directories of input project")
    val scalafixTestkitOutputSourceDirectories: TaskKey[Seq[File]] =
      taskKey[Seq[File]]("Source directories of output project")
  }
  import autoImport._

  override def buildSettings: Seq[Def.Setting[?]] =
    List(
      // This makes it simpler to use sbt-scalafix SNAPSHOTS: such snapshots may bring scalafix-* SNAPSHOTS which is fine in the
      // meta build as the same resolver (declared in project/plugins.sbt) is used. However, since testkit-enabled projects are
      // built against a version of scalafix-testkit dictated by scalafix.sbt.BuildInfo.scalafixVersion, the same resolver is
      // needed here as well. Unfortunately it works only for sbt 1.7+, see https://github.com/sbt/sbt/pull/6849.
      includePluginResolvers := true
    )

  override def projectSettings: Seq[Def.Setting[?]] =
    List(
      libraryDependencies +=
        ("ch.epfl.scala" % "scalafix-testkit" % BuildInfo.scalafixVersion % Test)
          .cross(CrossVersion.full),
      scalafixTestkitInputScalacOptions := scalacOptions.value,
      scalafixTestkitInputScalaVersion := scalaVersion.value,
      Test / resourceGenerators += Def.task {
        implicit val conv: FileConverter = fileConverter.value

        val props = new java.util.Properties()
        val values = Map[String, Seq[File]](
          "sourceroot" ->
            List((ThisBuild / baseDirectory).value),
          "inputClasspath" ->
            scalafixTestkitInputClasspath.value.map(Compat.toFile),
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
          (Test / managedResourceDirectories).value.head /
            "scalafix-testkit.properties"
        IO.write(props, "Input data for scalafix testkit", out)
        Seq(out)
      }
    )
}
