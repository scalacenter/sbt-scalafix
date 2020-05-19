package scalafix.internal.sbt

import java.net.URLClassLoader

import com.geirsson.coursiersmall.Repository
import sbt._
import sbt.internal.sbtscalafix.Compat
import scalafix.interfaces.{ScalafixArguments, Scalafix => ScalafixAPI}

class ScalafixInterface private (
    val args: ScalafixArguments,
    private val toolClasspath: URLClassLoader
) {

  // Accumulates the classpath via classloader delegation, as args.withToolClasspath() only considers the last call.
  //
  // We effectively end up with the following class loader hierarchy:
  //   1. Meta-project sbt class loader
  //       - bound to the sbt session
  //   2. ScalafixInterfacesClassloader, loading `scalafix-interfaces` from its parent
  //       - bound to the sbt session
  //   3. `scalafix-cli` JARs
  //       - bound to the sbt session
  //   4. Global, external dependencies
  //       - present only if custom dependencies were defined
  //       - used for rule names autocompletion
  //       - bound to the sbt session
  //   5. Project-level, internal and external classpath for ScalafixConfig ivy configuration & dependencies for
  //      CLI-requested `dependency:` rules
  //       - present only on project-specific (local rules) or invocation-specific (`dependency:` rule) classpath
  //       - recreated at each task invocation
  def addToolClasspath(
      extraExternalDeps: Seq[ModuleID],
      customResolvers: Seq[Repository],
      extraInternalDeps: Seq[File]
  ): ScalafixInterface = {
    if (extraExternalDeps.isEmpty && extraInternalDeps.isEmpty) {
      this
    } else {
      val extraURLs = ScalafixCoursier
        .scalafixToolClasspath(
          extraExternalDeps,
          customResolvers
        ) ++ extraInternalDeps.map(_.toURI.toURL)

      val newToolClasspath =
        new URLClassLoader(extraURLs.toArray, toolClasspath)
      new ScalafixInterface(
        args.withToolClasspath(newToolClasspath),
        newToolClasspath
      )
    }
  }
}

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
      val jars = ScalafixCoursier.scalafixCliJars(scalafixCustomResolvers)
      val urls = jars.map(_.toUri.toURL).toArray
      val interfacesParent =
        new ScalafixInterfacesClassloader(this.getClass.getClassLoader)
      val classloader = new URLClassLoader(urls, interfacesParent)
      val api = ScalafixAPI.classloadInstance(classloader)
      val callback = new ScalafixLogger(logger)

      val args = api
        .newArguments()
        .withMainCallback(callback)

      new ScalafixInterface(args, classloader).addToolClasspath(
        scalafixDependencies,
        scalafixCustomResolvers,
        Nil
      )
    })
}
