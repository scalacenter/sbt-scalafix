package scalafix.internal.sbt

import java.net.URLClassLoader
import java.nio.file.Path
import java.{util => jutil}

import com.geirsson.coursiersmall.Repository
import sbt._
import sbt.internal.sbtscalafix.Compat
import scalafix.interfaces.{Scalafix => ScalafixAPI, _}
import scalafix.sbt.InvalidArgument

import scala.collection.JavaConverters._
import scala.util.control.NonFatal

sealed trait Arg extends (ScalafixArguments => ScalafixArguments)

object Arg {

  sealed trait CacheKey

  case class ToolClasspath(classLoader: URLClassLoader)
      extends Arg
      with CacheKey {
    override def apply(sa: ScalafixArguments): ScalafixArguments =
      // this effectively overrides any previous URLClassLoader
      sa.withToolClasspath(classLoader)
  }

  case class Rules(rules: Seq[String]) extends Arg with CacheKey {
    override def apply(sa: ScalafixArguments): ScalafixArguments =
      sa.withRules(rules.asJava)
  }

  case class Paths(paths: Seq[Path]) extends Arg { // this one is extracted/stamped directly
    override def apply(sa: ScalafixArguments): ScalafixArguments =
      sa.withPaths(paths.asJava)
  }

  case class Config(file: Option[Path]) extends Arg with CacheKey {
    override def apply(sa: ScalafixArguments): ScalafixArguments =
      sa.withConfig(jutil.Optional.ofNullable(file.orNull))
  }

  case class ParsedArgs(args: Seq[String]) extends Arg with CacheKey {
    override def apply(sa: ScalafixArguments): ScalafixArguments =
      sa.withParsedArguments(args.asJava)
  }

  case class ScalaVersion(version: String) extends Arg { //FIXME: with CacheKey {
    override def apply(sa: ScalafixArguments): ScalafixArguments =
      sa.withScalaVersion(version)
  }

  case class ScalacOptions(options: Seq[String]) extends Arg { //FIXME: with CacheKey {
    override def apply(sa: ScalafixArguments): ScalafixArguments =
      sa.withScalacOptions(options.asJava)
  }

  case class Classpath(classpath: Seq[Path]) extends Arg { //FIXME: with CacheKey {
    override def apply(sa: ScalafixArguments): ScalafixArguments =
      sa.withClasspath(classpath.asJava)
  }

  case object NoCache extends Arg with CacheKey {
    override def apply(sa: ScalafixArguments): ScalafixArguments =
      sa // caching is currently implemented in sbt-scalafix itself
  }

  case class PrintStream(printStream: java.io.PrintStream) extends Arg {
    override def apply(sa: ScalafixArguments): ScalafixArguments =
      sa.withPrintStream(printStream)
  }
}

class ScalafixInterface private (
    scalafixArguments: ScalafixArguments, // hide it to force usage of withArgs so we can intercept arguments
    val args: Seq[Arg]
) {

  private val lastToolClasspath = args.reverse
    .collectFirst { case tcp: Arg.ToolClasspath => tcp }
    .getOrElse(
      throw new IllegalArgumentException(
        "a base toolClasspath must be provided"
      )
    )
    .classLoader

  private def this(
      api: ScalafixAPI,
      toolClasspath: URLClassLoader,
      mainCallback: ScalafixMainCallback
  ) =
    this(
      api
        .newArguments()
        .withMainCallback(mainCallback)
        .withToolClasspath(toolClasspath),
      Seq(Arg.ToolClasspath(toolClasspath))
    )

  // Accumulates the classpath via classloader delegation, as only the last Arg.ToolClasspath is considered
  //
  // We effectively end up with the following class loader hierarchy:
  //   1. Meta-project sbt class loader
  //       - bound to the sbt session
  //   2. ScalafixInterfacesClassloader, loading `scalafix-interfaces` from its parent
  //       - bound to the sbt session
  //   3. `scalafix-cli` JARs
  //       - passed in the constructor
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
    val extraURLs = ScalafixCoursier
      .scalafixToolClasspath(
        extraExternalDeps,
        customResolvers
      ) ++ extraInternalDeps.map(_.toURI.toURL)

    if (extraURLs.isEmpty) this
    else {
      val classpath = new URLClassLoader(extraURLs.toArray, lastToolClasspath)
      withArgs(Arg.ToolClasspath(classpath))
    }
  }

  def withArgs(args: Arg*): ScalafixInterface = {
    val newScalafixArguments = args.foldLeft(scalafixArguments) { (acc, arg) =>
      try arg(acc)
      catch { case NonFatal(e) => throw new InvalidArgument(e.getMessage) }
    }
    new ScalafixInterface(newScalafixArguments, this.args ++ args)
  }

  def run(): Seq[ScalafixError] =
    scalafixArguments.run().toSeq

  def availableRules(): Seq[ScalafixRule] =
    scalafixArguments.availableRules().asScala

  def rulesThatWillRun(): Seq[ScalafixRule] =
    try scalafixArguments.rulesThatWillRun().asScala
    catch { case NonFatal(e) => throw new InvalidArgument(e.getMessage) }

  def validate(): Option[ScalafixException] =
    Option(scalafixArguments.validate().orElse(null))

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
      new ScalafixInterface(api, classloader, callback)
        .addToolClasspath(
          scalafixDependencies,
          scalafixCustomResolvers,
          Nil
        )
    })
}
