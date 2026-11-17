package scalafix.internal.sbt

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

import scala.jdk.CollectionConverters.*

import sbt.*
import sbt.Keys.*
import sbt.io.Hash
import sbt.util.CacheImplicits.given

import scalafix.interfaces.ScalafixError
import scalafix.sbt.BuildInfo
import scalafix.sbt.ScalafixFailed
import xsbti.FileConverter

/** sbt 2.x task-cache integration for Scalafix's source-rewriting effect.
  *
  * Scalafix edits sources, which are outside sbt's cache output directory and
  * therefore cannot be declared as sbt 2.x task outputs. The legacy tracker
  * still selects stale files and applies the rules. We snapshot those files in
  * a declared output archive, then restore/apply that archive in an uncached
  * task on cache hits.
  */
private[sbt] object Sbt2ScalafixCache {

  private val CacheProtocolVersion = "3"

  def task(
      interface: ScalafixInterface,
      files: Seq[Path],
      sourceStamps: Seq[(Path, sbt.nio.FileStamp)],
      streams: TaskStreams,
      fileConverter: FileConverter,
      baseDirectory: Path,
      check: Boolean,
      run: () => Set[File],
      sbtVersion: String
  ): Def.Initialize[Task[Unit]] = {
    if (files.isEmpty) Def.task(())
    else {
      val outputDirectory =
        streams.cacheDirectory.toPath.resolve("sbt2-scalafix-cache")
      val outputNames = files.indices.map(index => s"$index.scala")
      val outputArchive = outputDirectory.resolve("rewrites.zip")

      // Recompute the file and invocation fingerprints on every invocation.
      // Their value is then an ordinary, serializable input to the cached task.
      val inputs = Def.task {
        Def.uncached(
          cacheInputs(
            interface,
            files,
            sourceStamps,
            baseDirectory,
            sbtVersion,
            rootPaths.value
          )
        )
      }

      val cachedEvaluation = Def.cachedTask {
        val _ = inputs.value
        val rewrittenFiles = run()
        val indexByPath = files.zipWithIndex.map { case (path, index) =>
          path.toAbsolutePath.normalize() -> index
        }.toMap
        val rewrittenIndices = rewrittenFiles
          .flatMap(file =>
            indexByPath.get(file.toPath.toAbsolutePath.normalize())
          )
          .toSeq
          .distinct
          .sorted
        val rewrittenSources = rewrittenIndices.map { index =>
          outputNames(index) -> Files.readAllBytes(files(index))
        }
        writeArchive(outputArchive, rewrittenSources)
        val output = fileConverter.toVirtualFile(outputArchive)
        Def.declareOutput(output)
        output: xsbti.HashedVirtualFileRef
      }

      Def.task {
        val cachedOutput = cachedEvaluation.value
        Def.uncached {
          val archive = fileConverter.toPath(cachedOutput)
          val cachedFiles = readArchive(archive)
          val rewrites = files.zipWithIndex.flatMap { case (source, index) =>
            cachedFiles.get(outputNames(index)).map(source -> _)
          }

          if (check && rewrites.nonEmpty) {
            streams.log.error(
              s"Scalafix check failed: ${rewrites.size} Scala source(s) would be rewritten"
            )
            throw new ScalafixFailed(List(ScalafixError.TestError))
          }

          if (!check)
            rewrites.foreach { case (source, rewritten) =>
              Files.write(source, rewritten)
            }
        }
      }
    }
  }

  private def cacheInputs(
      interface: ScalafixInterface,
      files: Seq[Path],
      sourceStamps: Seq[(Path, sbt.nio.FileStamp)],
      baseDirectory: Path,
      sbtVersion: String,
      rootPaths: Map[String, Path]
  ): Seq[String] = {
    val inputs = Vector.newBuilder[String]
    inputs += s"cacheProtocol=$CacheProtocolVersion"
    inputs += s"sbtVersion=$sbtVersion"
    inputs += s"scalafix=${BuildInfo.scalafixVersion}"
    inputs += s"scalameta=${BuildInfo.scalametaVersion}"
    val stampsByPath = sourceStamps.map { case (path, stamp) =>
      path.toAbsolutePath.normalize() -> stamp.toString
    }.toMap
    inputs ++= files.zipWithIndex.map { case (path, index) =>
      val sourceStamp = stampsByPath
        .get(path.toAbsolutePath.normalize())
        .getOrElse(stamp(path))
      s"source[$index]=${relativePath(path, baseDirectory, rootPaths)}:$sourceStamp"
    }

    interface.args.foreach {
      case Arg.ToolClasspath(uris, dependencies, _) =>
        inputs ++= dependencies.map(dependency => s"dependency=$dependency")
        inputs ++= uris.zipWithIndex.map { case (uri, index) =>
          val path = java.nio.file.Paths.get(uri)
          s"tool[$index]=${relativePath(path, baseDirectory, rootPaths)}:${stamp(path)}"
        }
      case Arg.Rules(_) =>
        () // Effective rules are recorded below, after Scalafix has applied CLI/config selection.
      case Arg.Config(config) =>
        val path = config.getOrElse(baseDirectory.resolve(".scalafix.conf"))
        inputs += s"config=${relativePath(path, baseDirectory, rootPaths)}:${stamp(path)}"
      case Arg.ParsedArgs(args) =>
        val cacheRelevantArgs =
          args.filterNot(Set("--check", "--test", "--syntactic"))
        inputs ++= normalizeArguments(cacheRelevantArgs, baseDirectory)
          .map(arg => s"arg=$arg")
      case Arg.ScalaVersion(version) => inputs += s"scalaVersion=$version"
      case Arg.ScalacOptions(options) =>
        val normalized = options.map(option =>
          normalizeScalacOption(option, baseDirectory, rootPaths)
        )
        inputs ++= normalized.map { case (option, _) =>
          s"scalacOption=$option"
        }
        inputs ++= normalized
          .flatMap(_._2)
          .map(stamp => s"scalacOptionFile=$stamp")
      case Arg.Classpath(classpath) =>
        inputs ++= classpath.zipWithIndex.map { case (path, index) =>
          s"classpath[$index]=${relativePath(path, baseDirectory, rootPaths)}:${stamp(path)}"
        }
      case Arg.Paths(_) | Arg.NoCache | _: Arg.PrintStream => ()
    }
    inputs ++= interface
      .rulesThatWillRun()
      .map(rule => s"effectiveRule=${rule.name()}")
    inputs.result()
  }

  private def normalizeArguments(
      args: Seq[String],
      baseDirectory: Path
  ): Seq[String] = {
    val base = baseDirectory.toAbsolutePath.normalize().toString
    args.map(_.replace(base, "${BASE}"))
  }

  private def normalizeScalacOption(
      option: String,
      baseDirectory: Path,
      rootPaths: Map[String, Path]
  ): (String, Seq[String]) = {
    val base = baseDirectory.toAbsolutePath.normalize()
    val rootPathsByName = rootPaths.map { case (name, path) =>
      name -> path.toAbsolutePath.normalize()
    }
    val pathStamps = Vector.newBuilder[String]
    val virtualPath = "\\$\\{([^}]+)\\}/([^,:]+)".r
    val withVirtualStamps = virtualPath.replaceAllIn(
      option,
      matched =>
        rootPathsByName.get(matched.group(1)).foreach { root =>
          val path = root.resolve(matched.group(2)).normalize()
          pathStamps += s"${matched.group(1)}/${matched.group(2)}:${stamp(path)}"
        }
        java.util.regex.Matcher.quoteReplacement(matched.matched)
    )
    val absolutePath =
      "(?<![A-Za-z0-9])(?:[A-Za-z]:[\\\\/][^,:]+|/(?:[^,:]+))".r
    val normalized = absolutePath.replaceAllIn(
      withVirtualStamps,
      matched =>
        try {
          val path =
            java.nio.file.Paths.get(matched.matched).toAbsolutePath.normalize()
          val matchingRoot = rootPathsByName.toSeq
            .filter { case (_, root) => path.startsWith(root) }
            .sortBy { case (name, root) => (-root.getNameCount, name) }
            .headOption
          val replacement = matchingRoot match {
            case Some((name, root)) =>
              val relative = root.relativize(path).toString
              pathStamps += s"$name/$relative:${stamp(path)}"
              s"$${$name}/$relative"
            case None if path.startsWith(base) =>
              val relative = base.relativize(path).toString
              pathStamps += s"BASE/$relative:${stamp(path)}"
              s"$${BASE}/$relative"
            case None =>
              pathStamps += s"${path}:${stamp(path)}"
              matched.matched
          }
          java.util.regex.Matcher.quoteReplacement(replacement)
        } catch {
          case _: Exception => matched.matched
        }
    )
    normalized -> pathStamps.result()
  }

  private def relativePath(
      path: Path,
      baseDirectory: Path,
      rootPaths: Map[String, Path]
  ): String = {
    val normalized = path.toAbsolutePath.normalize()
    val base = baseDirectory.toAbsolutePath.normalize()
    val matchingRoot = rootPaths
      .map { case (name, root) => name -> root.toAbsolutePath.normalize() }
      .toSeq
      .filter { case (_, root) => normalized.startsWith(root) }
      .sortBy { case (name, root) => (-root.getNameCount, name) }
      .headOption
    matchingRoot match {
      case Some((name, root)) => s"$${$name}/${root.relativize(normalized)}"
      case None if normalized.startsWith(base) =>
        base.relativize(normalized).toString
      case None => normalized.toString
    }
  }

  private def stamp(path: Path): String = {
    if (Files.isRegularFile(path)) digestHex(Hash(path.toFile))
    else if (Files.isDirectory(path)) {
      val digest = MessageDigest.getInstance("SHA-256")
      val stream = Files.walk(path)
      try {
        stream
          .iterator()
          .asScala
          .filter(Files.isRegularFile(_))
          .toSeq
          .sortBy(_.toString)
          .foreach { file =>
            digest.update(
              path.relativize(file).toString.getBytes(StandardCharsets.UTF_8)
            )
            digest.update(
              digestHex(Hash(file.toFile)).getBytes(StandardCharsets.UTF_8)
            )
          }
      } finally stream.close()
      digest.digest().map(byte => f"${byte & 0xff}%02x").mkString
    } else "missing"
  }

  private def digestHex(bytes: Array[Byte]): String =
    bytes.map(byte => f"${byte & 0xff}%02x").mkString

  private def writeArchive(
      path: Path,
      files: Seq[(String, Array[Byte])]
  ): Unit = {
    Files.createDirectories(path.getParent)
    Files.deleteIfExists(path)
    val output = new ZipOutputStream(Files.newOutputStream(path))
    try
      files.foreach { case (name, content) =>
        val entry = new ZipEntry(name)
        entry.setTime(0L)
        output.putNextEntry(entry)
        output.write(content)
        output.closeEntry()
      }
    finally output.close()
  }

  private def readArchive(path: Path): Map[String, Array[Byte]] = {
    val zip = new ZipFile(path.toFile)
    try {
      zip
        .entries()
        .asScala
        .filterNot(_.isDirectory)
        .map { entry =>
          val input = zip.getInputStream(entry)
          try entry.getName -> input.readAllBytes()
          finally input.close()
        }
        .toMap
    } finally zip.close()
  }

}
