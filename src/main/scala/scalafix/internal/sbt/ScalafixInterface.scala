package scalafix.internal.sbt

import java.nio.file.Path
import java.{util => jutil}

import coursierapi.Repository
import sbt._
import sbt.internal.sbtscalafix.Compat
import scalafix.interfaces.{Scalafix => ScalafixAPI, _}
import scalafix.sbt.InvalidArgument

import scala.collection.JavaConverters._
import scala.util.Try
import scala.util.control.NonFatal

sealed trait Arg extends (ScalafixArguments => ScalafixArguments)

object Arg {

  sealed trait CacheKey

  case class ToolClasspath(
      customURIs: Seq[URI],
      customDependencies: Seq[ModuleID],
      repositories: Seq[Repository]
  ) extends Arg
      with CacheKey {
    override def apply(sa: ScalafixArguments): ScalafixArguments =
      sa.withToolClasspath(
        customURIs.map(_.toURL).asJava,
        customDependencies.map(_.asCoursierCoordinates).asJava,
        repositories.asJava
      )

    private implicit class XtensionModuleID(m: ModuleID) {
      def asCoursierCoordinates: String = {
        m.crossVersion match {
          case Compat.Disabled =>
            s"${m.organization}:${m.name}:${m.revision}"
          case _: CrossVersion.Binary =>
            s"${m.organization}::${m.name}:${m.revision}"
          case _: CrossVersion.Full =>
            s"${m.organization}:::${m.name}:${m.revision}"
          case other =>
            throw new InvalidArgument(
              s"Unsupported crossVersion $other for dependency $m"
            )
        }
      }
    }
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
    postScalafixHooks: Seq[Seq[File] => Unit],
    val args: Seq[Arg]
) {

  def withArgs(args: Arg*): ScalafixInterface = {
    val newScalafixArguments = args.foldLeft(scalafixArguments) { (acc, arg) =>
      try arg(acc)
      catch { case NonFatal(e) => throw new InvalidArgument(e.getMessage) }
    }
    new ScalafixInterface(
      newScalafixArguments,
      postScalafixHooks,
      this.args ++ args
    )
  }

  def run(): Seq[ScalafixError] =
    try {
      scalafixArguments.run().toSeq
    } finally {
      val mayRewrite = args.reverse
        .collectFirst {
          case Arg.ParsedArgs(args) => args
        }
        .forall { args =>
          Seq("--check", "--test", "--auto-suppress-linter-errors")
            .intersect(args)
            .isEmpty
        }
      if (mayRewrite) for {
        hook <- postScalafixHooks
        files <- args.reverse.collectFirst {
          case Arg.Paths(paths) => paths.map(_.toFile)
        }
      } yield Try(hook(files))
    }

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
      scalafixBinaryScalaVersion: String,
      scalafixDependencies: Seq[ModuleID],
      scalafixCustomResolvers: Seq[Repository],
      logger: Logger = Compat.ConsoleLogger(System.out),
      postScalafixHooks: Seq[Seq[File] => Unit] = Nil
  ): () => ScalafixInterface =
    new LazyValue({ () =>
      val callback = new ScalafixLogger(logger)
      val scalafixArguments = ScalafixAPI
        .fetchAndClassloadInstance(
          scalafixBinaryScalaVersion,
          scalafixCustomResolvers.asJava
        )
        .newArguments()
        .withMainCallback(callback)
      new ScalafixInterface(scalafixArguments, postScalafixHooks, Nil)
        .withArgs(
          Arg.ToolClasspath(
            Nil,
            scalafixDependencies,
            scalafixCustomResolvers
          )
        )
    })
}
