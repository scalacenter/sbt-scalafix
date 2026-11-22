package scalafix.internal.sbt

import java.io.File
import java.net.MalformedURLException
import java.nio.file.Paths

import scala.jdk.CollectionConverters.*

import sbt.librarymanagement.{MavenRepository as _, *}
import sbt.util.Logger

import coursierapi.Credentials
import coursierapi.IvyRepository
import coursierapi.MavenRepository
import coursierapi.Repository
import org.apache.ivy.plugins.resolver.IBiblioResolver

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

  // Path.toUri only appends a trailing slash for existing directories, so
  // preserve the separator explicitly: the directory may not exist yet (e.g. a
  // fresh custom ivy home), and dropping it would glue the placeholder to the
  // path segment ("local[organisation]/...").
  private def toUriString(path: String): String = {
    val uri = Paths.get(path).toUri.toASCIIString
    if (path.endsWith("/") && !uri.endsWith("/")) uri + "/"
    else uri
  }

  // Convert a local path (potentially with Ivy pattern placeholders) to a
  // file URI string, encoding characters like whitespace along the way via
  // Paths.get(...).toUri. Remote URIs (containing "://") are returned as-is.
  private[sbt] def pathToUriString(path: String): String = {
    val stopAtIdx = path.indexWhere(c => c == '[' || c == '$' || c == '(')
    if (path.contains("://")) {
      // Already a URI, return as-is. On Windows, Paths.get() would throw
      // InvalidPathException for such strings, see
      // https://github.com/scalacenter/scalafix/issues/2363
      path
    } else if (stopAtIdx > 0) {
      // Encode only the path prefix (before placeholders) as a file URI,
      // then re-append the placeholder suffix as-is
      val (pathPart, patternPart) = path.splitAt(stopAtIdx)
      toUriString(pathPart) + patternPart
    } else if (stopAtIdx == 0)
      "file://" + path
    else
      toUriString(path)
  }

  // sbt expands ${ivy.home} in its Ivy patterns, but coursier's interface
  // cannot carry Ivy properties, so such patterns must be expanded before
  // being handed over. Repositories still containing other unresolved
  // properties are rejected by coursier ("Property(ies) not found") and are
  // therefore skipped.
  private val propertyRef = "\\$\\{([^}]+)}".r

  // Avoids triggering -Xlint:missing-interpolator on the literal.
  private val ivyHomeProperty = "$" + "{ivy.home}"

  // Same precedence as sbt's own resolution (see
  // sbt.internal.librarymanagement.LMSysProp.defaultIvyHome): sbt.ivy.home,
  // then ivy.home, then ~/.ivy2.
  private def sbtIvyHome: String =
    sys.props
      .get("sbt.ivy.home")
      .orElse(sys.props.get("ivy.home"))
      .map(new File(_))
      .getOrElse(new File(sys.props("user.home"), ".ivy2"))
      .getAbsolutePath

  private[sbt] def expandIvyHome(pattern: String): String =
    pattern.replace(ivyHomeProperty, sbtIvyHome)

  private def ivyRepositoryOpt(
      artifactPattern: String,
      ivyPattern: String,
      log: Logger
  ): Option[Repository] = {
    val expandedArtifactPattern = expandIvyHome(artifactPattern)
    val expandedIvyPattern = expandIvyHome(ivyPattern)
    val props =
      propertyRef
        .findAllMatchIn(expandedArtifactPattern)
        .map(m => m.group(1))
        .toList ++
        propertyRef
          .findAllMatchIn(expandedIvyPattern)
          .map(m => m.group(1))
          .toList
    if (props.isEmpty)
      Some(
        IvyRepository
          .of(pathToUriString(expandedArtifactPattern))
          .withMetadataPattern(pathToUriString(expandedIvyPattern))
          .withDropInfoAttributes(true)
      )
    else {
      log.warn(
        s"Repository pattern $artifactPattern contains Ivy property reference(s) ${props.distinct
            .mkString(", ")} that coursier cannot resolve, ignoring it"
      )
      None
    }
  }

  def repository(
      resolver: Resolver,
      log: Logger,
      credentialsByHost: Map[String, Credentials],
      allowIvyRawRepositories: Boolean
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
            ivyRepositoryOpt(
              r.patterns.artifactPatterns.head,
              r.patterns.ivyPatterns.head,
              log
            )

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
      case raw: RawRepository if allowIvyRawRepositories =>
        ibiblioPatterns(raw).filter(patternMatchGuard) match {
          case Some(patterns) =>
            parseMavenCompatResolver(log, patterns, credentialsByHost)
          case None =>
            log.warn(s"Unrecognized repository ${raw.name}, ignoring it")
            None
        }

      case other =>
        log.warn(s"Unrecognized repository ${other.name}, ignoring it")
        None
    }

  private def patternMatchGuard(patterns: Patterns): Boolean =
    patterns.ivyPatterns.lengthCompare(1) == 0 &&
      patterns.artifactPatterns.lengthCompare(1) == 0

  private def ibiblioPatterns(raw: RawRepository): Option[Patterns] =
    raw.resolver match {
      case resolver: IBiblioResolver =>
        def strings(values: java.util.List[?]): Vector[String] =
          Option(values)
            .map(_.asScala.toVector)
            .getOrElse(Vector.empty)
            .collect { case value: String =>
              value
            }

        Some(
          Patterns(
            ivyPatterns = strings(resolver.getIvyPatterns),
            artifactPatterns = strings(resolver.getArtifactPatterns),
            isMavenCompatible = resolver.isM2compatible,
            descriptorOptional = !resolver.isUseMavenMetadata,
            skipConsistencyCheck = !resolver.isCheckconsistency
          )
        )
      case _ => None
    }

  private def parseMavenCompatResolver(
      log: Logger,
      patterns: Patterns,
      credentialsByHost: Map[String, Credentials]
  ): Option[Repository] = {
    val mavenCompatibleBaseOpt0 = mavenCompatibleBaseOpt(patterns)

    mavenCompatibleBaseOpt0 match {
      case None =>
        ivyRepositoryOpt(
          patterns.artifactPatterns.head,
          patterns.ivyPatterns.head,
          log
        )

      case Some(mavenCompatibleBase) =>
        mavenRepositoryOpt(mavenCompatibleBase, log, credentialsByHost)
    }
  }
}
