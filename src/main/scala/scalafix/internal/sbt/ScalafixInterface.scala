package scalafix.internal.sbt

import java.nio.file.Path
import java.{util => jutil}
import coursierapi.Repository
import sbt._
import scalafix.interfaces.{Scalafix => ScalafixAPI, _}
import scalafix.sbt.InvalidArgument

import scala.collection.JavaConverters._
import java.io.PrintStream

sealed trait Arg extends (ScalafixArguments => ScalafixArguments)

object Arg {

  sealed trait CacheKey

  case class ToolClasspath(
      customURIs: Seq[URI],
      customDependencies: Seq[ModuleID],
      repositories: Seq[Repository]
  ) extends Arg
      with CacheKey {

    import scalafix.internal.sbt.Implicits._

    override def apply(sa: ScalafixArguments): ScalafixArguments =
      sa.withToolClasspath(
        customURIs.map(_.toURL).asJava,
        customDependencies.map(_.asCoursierCoordinates).asJava,
        repositories.asJava
      )
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

  case class ScalaVersion(version: String) extends Arg { // FIXME: with CacheKey {
    override def apply(sa: ScalafixArguments): ScalafixArguments =
      sa.withScalaVersion(version)
  }

  case class ScalacOptions(options: Seq[String]) extends Arg { // FIXME: with CacheKey {
    override def apply(sa: ScalafixArguments): ScalafixArguments =
      sa.withScalacOptions(options.asJava)
  }

  case class Classpath(classpath: Seq[Path]) extends Arg { // FIXME: with CacheKey {
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

  def withArgs(args: Arg*): ScalafixInterface = {
    val newScalafixArguments = args.foldLeft(scalafixArguments) { (acc, arg) =>
      try arg(acc)
      catch {
        case e: ScalafixException => throw new InvalidArgument(e.getMessage)
      }
    }
    new ScalafixInterface(newScalafixArguments, this.args ++ args)
  }

  def run(): Seq[ScalafixError] =
    scalafixArguments.run().toSeq

  def availableRules(): Seq[ScalafixRule] =
    scalafixArguments.availableRules().asScala

  def rulesThatWillRun(): Seq[ScalafixRule] =
    try scalafixArguments.rulesThatWillRun().asScala
    catch {
      case e: ScalafixException => throw new InvalidArgument(e.getMessage)
    }

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
      logger: Logger = ConsoleLogger(System.out)
  ): () => ScalafixInterface =
    new LazyValue({ () =>
      if (scalafixBinaryScalaVersion.startsWith("3"))
        logger.error(
          "To use Scalafix on Scala 3 projects, you must unset `scalafixBinaryScalaVersion`. " +
            "Rules such as ExplicitResultTypes requiring the project version to match the Scalafix " +
            "version are unsupported for the moment."
        )
      else if (scalafixBinaryScalaVersion == "2.11")
        logger.error(
          "Scala 2.11 is no longer supported. Please downgrade to the final version supporting " +
            "it: sbt-scalafix 0.10.4."
        )
      val callback = new ScalafixLogger(logger)
      val scalafixArguments = ScalafixAPI
        .fetchAndClassloadInstance(
          scalafixBinaryScalaVersion,
          scalafixCustomResolvers.asJava
        )
        .newArguments()
        .withMainCallback(callback)
      val printStream =
        new PrintStream(
          LoggingOutputStream(
            logger,
            Level.Info
          )
        )
      new ScalafixInterface(scalafixArguments, Nil)
        .withArgs(
          Arg.PrintStream(printStream),
          Arg.ToolClasspath(
            Nil,
            scalafixDependencies,
            scalafixCustomResolvers
          )
        )
    })
}
