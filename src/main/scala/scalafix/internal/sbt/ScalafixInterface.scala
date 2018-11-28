package scalafix.internal.sbt

import java.net.URLClassLoader

import com.geirsson.coursiersmall.Repository
import sbt._
import sbt.internal.sbtscalafix.Compat
import scalafix.interfaces.{ScalafixArguments, Scalafix => ScalafixAPI}

case class ScalafixInterface(api: ScalafixAPI, args: ScalafixArguments)
object ScalafixInterface {
  private class LazyValue[T](thunk: () => T) extends (() => T) {
    private lazy val _value = scala.util.Try(thunk())
    override def apply(): T = _value.get
  }
  def fromToolClasspath(
      scalafixDependencies: Seq[ModuleID],
      scalafixCustomResolvers: Seq[Repository],
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
        scalafixCustomResolvers,
        classloader
      )
      val callback = new ScalafixLogger(logger)

      val args = api
        .newArguments()
        .withToolClasspath(toolClasspath)
        .withMainCallback(callback)

      ScalafixInterface(api, args)
    })
}
