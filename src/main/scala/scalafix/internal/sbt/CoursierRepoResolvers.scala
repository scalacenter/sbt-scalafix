package scalafix.internal.sbt

import coursierapi.{Credentials, IvyRepository, MavenRepository, Repository}
import org.apache.ivy.plugins.resolver.IBiblioResolver
import sbt.librarymanagement.{Configuration as _, MavenRepository as _, *}
import sbt.util.Logger

import java.net.MalformedURLException
import java.nio.file.Paths
import scala.collection.JavaConverters.*

// vendor from sbt-coursier/lmcoursier.internal.Resolvers
object CoursierRepoResolvers {

  private def mavenCompatibleBaseOpt(patterns: Patterns): Option[String] =
    if (patterns.isMavenCompatible) {
      // input  : /Users/user/custom/repo/[organisation]/[module](_[scalaVersion])(_[sbtVersion])/[revision]/[artifact]-[revision](-[classifier]).[ext]
      // output : /Users/user/custom/repo/
      def basePattern(pattern: String): String =
        pattern.takeWhile(c => c != '[' && c != '(')

      val baseIvyPattern = basePattern(patterns.ivyPatterns.head)
      val baseArtifactPattern = basePattern(patterns.artifactPatterns.head)

      if (baseIvyPattern == baseArtifactPattern)
        Some(baseIvyPattern)
      else
        None
    } else
      None

  private def mavenRepositoryOpt(
      root: String,
      log: Logger,
      credentialsByHost: Map[String, Credentials]
  ): Option[MavenRepository] =
    try {
      val rootURI = java.net.URI.create(
        root
      ) // ensure root is a URL whose protocol can be handled here
      val root0 = if (root.endsWith("/")) root else root + "/"
      val mavenRepository = MavenRepository.of(root0)

      Some(
        credentialsByHost
          .get(rootURI.getHost)
          .fold(mavenRepository)(mavenRepository.withCredentials)
      )
    } catch {
      case e: MalformedURLException =>
        log.warn(
          "Error parsing Maven repository base " +
            root +
            Option(e.getMessage).fold("")(" (" + _ + ")") +
            ", ignoring it"
        )

        None
    }

  // this handles whitespace in path
  private def pathToUriString(path: String): String = {
    val stopAtIdx = path.indexWhere(c => c == '[' || c == '$' || c == '(')
    if (stopAtIdx > 0) {
      val (pathPart, patternPart) = path.splitAt(stopAtIdx)
      Paths.get(pathPart).toUri.toASCIIString + patternPart
    } else if (stopAtIdx == 0)
      "file://" + path
    else
      Paths.get(path).toUri.toASCIIString
  }

  def repository(
      resolver: Resolver,
      log: Logger,
      credentialsByHost: Map[String, Credentials]
  ): Option[Repository] =
    resolver match {
      case r: sbt.librarymanagement.MavenRepository =>
        mavenRepositoryOpt(r.root, log, credentialsByHost)

      case r: FileRepository
          if r.patterns.ivyPatterns.lengthCompare(1) == 0 &&
            r.patterns.artifactPatterns.lengthCompare(1) == 0 =>
        val mavenCompatibleBaseOpt0 = mavenCompatibleBaseOpt(r.patterns)

        mavenCompatibleBaseOpt0 match {
          case None =>
            val repo = IvyRepository
              .of(pathToUriString(r.patterns.artifactPatterns.head))
              .withMetadataPattern(pathToUriString(r.patterns.ivyPatterns.head))
              .withDropInfoAttributes(true)

            Some(repo)

          case Some(mavenCompatibleBase) =>
            mavenRepositoryOpt(
              pathToUriString(mavenCompatibleBase),
              log,
              credentialsByHost
            )
        }

      case r: URLRepository if patternMatchGuard(r.patterns) =>
        parseMavenCompatResolver(log, r.patterns, credentialsByHost)

      case raw: RawRepository if raw.name == "inter-project" => // sbt.RawRepository.equals just compares names anyway
        None

      // Pattern Match resolver-type-specific RawRepositories
      case IBiblioRepository(p) =>
        parseMavenCompatResolver(
          log,
          p,
          credentialsByHost
        )

      case other =>
        log.warn(s"Unrecognized repository ${other.name}, ignoring it")
        None
    }

  private object IBiblioRepository {

    private def stringVector(v: java.util.List[_]): Vector[String] =
      Option(v).map(_.asScala.toVector).getOrElse(Vector.empty).collect {
        case s: String => s
      }

    private def patterns(resolver: IBiblioResolver): Patterns = Patterns(
      ivyPatterns = stringVector(resolver.getIvyPatterns),
      artifactPatterns = stringVector(resolver.getArtifactPatterns),
      isMavenCompatible = resolver.isM2compatible,
      descriptorOptional = !resolver.isUseMavenMetadata,
      skipConsistencyCheck = !resolver.isCheckconsistency
    )

    def unapply(r: Resolver): Option[Patterns] =
      r match {
        case raw: RawRepository =>
          raw.resolver match {
            case b: IBiblioResolver =>
              Some(patterns(b))
                .filter(patternMatchGuard)
            case _ =>
              None
          }
        case _ =>
          None
      }
  }

  private def patternMatchGuard(patterns: Patterns): Boolean =
    patterns.ivyPatterns.lengthCompare(1) == 0 &&
      patterns.artifactPatterns.lengthCompare(1) == 0

  private def parseMavenCompatResolver(
      log: Logger,
      patterns: Patterns,
      credentialsByHost: Map[String, Credentials]
  ): Option[Repository] = {
    val mavenCompatibleBaseOpt0 = mavenCompatibleBaseOpt(patterns)

    mavenCompatibleBaseOpt0 match {
      case None =>
        val repo = IvyRepository
          .of(pathToUriString(patterns.artifactPatterns.head))
          .withMetadataPattern(pathToUriString(patterns.ivyPatterns.head))
          .withDropInfoAttributes(true)

        Some(repo)

      case Some(mavenCompatibleBase) =>
        mavenRepositoryOpt(mavenCompatibleBase, log, credentialsByHost)
    }
  }
}
