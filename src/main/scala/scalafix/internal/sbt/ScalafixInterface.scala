package scalafix.internal.sbt

import java.net.URLClassLoader
import sbt._
import sbt.internal.sbtscalafix.Compat
import scala.collection.JavaConverters._
import scalafix.interfaces.ScalafixMainArgs
import scalafix.interfaces.{Scalafix => ScalafixAPI}

case class ScalafixInterface(api: ScalafixAPI, args: ScalafixMainArgs)
object ScalafixInterface {
  private class LazyValue[T](thunk: () => T) extends (() => T) {
    private lazy val _value = scala.util.Try(thunk())
    override def apply(): T = _value.get
  }
  def fromToolClasspath(
      scalafixDependencies: Seq[ModuleID],
      logger: Logger = Compat.ConsoleLogger(System.out)
  ): () => ScalafixInterface =
    new LazyValue({ () =>
      val jars = ScalafixCoursier.scalafixCliJars
      val urls = jars.map(_.toUri.toURL).toArray
      val interfacesParent =
        new ScalafixInterfacesClassloader(this.getClass.getClassLoader)
      val classloader = new URLClassLoader(urls, interfacesParent)
      val api = ScalafixAPI.classloadInstance(classloader)
      val toolClasspath = ScalafixCoursier.scalafixToolClasspath(
        scalafixDependencies,
        classloader
      )
      val callback = new ScalafixLogger(logger)

      val args = api
        .newMainArgs()
        .withToolClasspath(toolClasspath)
        .withMainCallback(callback)

      ScalafixInterface(api, args)
    })
}
