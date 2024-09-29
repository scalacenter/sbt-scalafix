package scalafix.internal.sbt

import java.nio.file.Path
import java.{util => jutil}
import coursierapi.Repository
import sbt._
import sbt.io.RegularFileFilter
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

    def mutableFiles: Seq[File] =
      customURIs
        .map(uri => java.nio.file.Paths.get(uri).toFile)
        .flatMap {
          case classDirectory if classDirectory.isDirectory =>
            classDirectory.**(RegularFileFilter).get
          case jar =>
            Seq(jar)
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
    val args: Seq[Arg] = Nil
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

  type Cache = BlockingCache[
    (
        String, // scalafixScalaMajorMinorVersion
        Option[Arg.ToolClasspath]
    ),
    (
        ScalafixInterface,
        Seq[Long] // mutable files stamping
    )
  ]

  private[scalafix] val defaultLogger: Logger = ConsoleLogger(System.out)

  def apply(
      cache: Cache,
      scalafixScalaMajorMinorVersion: String,
      toolClasspath: Arg.ToolClasspath,
      logger: Logger,
      callback: ScalafixMainCallback
  ): ScalafixInterface = {

    // Build or retrieve from the cache the scalafix interface that can run
    // built-in rules. This is the most costly instantiation, which should be
    // shared as much as possible.
    val (buildinRulesInterface, _) = cache.compute(
      (
        scalafixScalaMajorMinorVersion,
        None
      ),
      {
        case Some(_) =>
          // cache hit, don't update
          None
        case None =>
          // cache miss, resolve scalafix artifacts and classload them
          if (scalafixScalaMajorMinorVersion == "2.11")
            logger.error(
              "Scala 2.11 is no longer supported. Please downgrade to the final version supporting " +
                "it: sbt-scalafix 0.10.4."
            )
          val scalafixArguments = ScalafixAPI
            .fetchAndClassloadInstance(
              scalafixScalaMajorMinorVersion,
              toolClasspath.repositories.asJava
            )
            .newArguments()
            .withMainCallback(callback)

          val printStream = Arg.PrintStream(
            new PrintStream(
              LoggingOutputStream(
                logger,
                Level.Info
              )
            )
          )

          Some(
            (
              new ScalafixInterface(scalafixArguments).withArgs(printStream),
              Nil
            )
          )
      }
    )

    // stamp the files that might change, to potentialy force invalidation
    // of the corresponding cache entry
    val currentStamps =
      toolClasspath.mutableFiles.map(IO.getModifiedTimeOrZero)

    val (toolClasspathInterface, _) = cache.compute(
      (
        scalafixScalaMajorMinorVersion,
        Some(toolClasspath)
      ),
      {
        case Some((_, oldStamps)) if (currentStamps == oldStamps) =>
          // cache hit, don't update
          None
        case _ =>
          // cache miss or stale stamps, resolve custom rules artifacts and classload them
          Some(
            (
              buildinRulesInterface.withArgs(toolClasspath),
              currentStamps
            )
          )
      }
    )

    toolClasspathInterface
  }
}
