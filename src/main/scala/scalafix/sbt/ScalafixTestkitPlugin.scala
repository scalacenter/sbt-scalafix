package scalafix.sbt

import sbt.Def
import sbt._
import sbt.Keys._
import java.io.File.pathSeparator
import sbt.plugins.JvmPlugin

object ScalafixTestkitPlugin extends AutoPlugin {
  override def trigger: PluginTrigger = noTrigger
  override def requires: Plugins = JvmPlugin
  object autoImport {
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
        val targetrootClasspath = semanticdbOption(
          scalafixTestkitInputScalacOptions.value,
          "targetroot"
        ).map(file).toSeq
        val values = Map[String, Seq[File]](
          "sourceroot" ->
            List(baseDirectory.in(ThisBuild).value),
          "inputClasspath" ->
            (scalafixTestkitInputClasspath.value
              .map(_.data) ++ targetrootClasspath),
          "inputSourceDirectories" ->
            scalafixTestkitInputSourceDirectories.value,
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

  private def semanticdbOption(
      scalacOptions: Seq[String],
      name: String
  ): Option[String] = {
    val flag = s"-P:semanticdb:$name:"
    scalacOptions
      .filter(_.startsWith(flag))
      .lastOption
      .map(_.stripPrefix(flag))
  }
}
