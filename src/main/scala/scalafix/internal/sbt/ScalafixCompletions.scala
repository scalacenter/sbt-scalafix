package scalafix.internal.sbt

import scalafix.interfaces.ScalafixRule

import java.io.File
import java.nio.file._
import java.util.regex.Pattern

import scala.util.control.NonFatal

import sbt.complete._
import sbt.complete.DefaultParsers._
import sbt.internal.sbtscalafix.JLineAccess

class ScalafixCompletions(
    workingDirectory: Path,
    loadedRules: List[ScalafixRule]
) {
  def this(file: File, loadedRules: List[ScalafixRule]) =
    this(file.toPath, loadedRules)

  private type P = Parser[String]
  private val space: P = token(Space).map(_.toString)
  private val string: P = StringBasic
  private def repsep(rep: P, sep: String): P =
    DefaultParsers.repsep(rep, sep: P).map(_.mkString(sep))
  private def arg(key: String, value: P): P =
    (key ~ space ~ value).map { case a ~ _ ~ c => a + " " + c }
  private def arg(key: String, shortKey: String, value: P): P =
    ((key | hide(shortKey)) ~ space ~ value).map {
      case a ~ _ ~ c => a + " " + c
    }

  private def mapOrFail[S, T](p: Parser[S])(f: S => T): Parser[T] =
    p.flatMap { s =>
      try {
        success(f(s))
      } catch {
        case NonFatal(e) => failure(e.toString)
      }
    }

  private def uri(protocol: String) =
    token(protocol + ":") ~> NotQuoted.map(x => s"$protocol:$x")
  private val filepathParser: P = {
    def toAbsolutePath(path: Path, workingDirectory: Path): Path = {
      if (path.isAbsolute) path
      else workingDirectory.resolve(path)
    }.normalize()

    // Extend FileExamples to tab complete when the prefix is an absolute path or `..`
    class AbsolutePathExamples(workingDirectory: Path, prefix: String = "")
        extends FileExamples(workingDirectory.toFile, prefix) {
      override def withAddedPrefix(addedPrefix: String): FileExamples = {

        val nextPrefix =
          if (addedPrefix.startsWith(".")) addedPrefix
          else prefix + addedPrefix
        val (b, p) = AbsolutePathCompleter.mkBase(nextPrefix, workingDirectory)
        new AbsolutePathExamples(b, p)
      }
    }
    object AbsolutePathCompleter {
      def mkBase(prefix: String, fallback: Path): (Path, String) = {
        val path = toAbsolutePath(Paths.get(prefix), fallback)
        if (prefix.endsWith(File.separator)) path -> ""
        else {
          if (path.getFileName != null)
            path.getParent -> path.getFileName.toString
          else fallback -> ""
        }
      }
    }

    string
      .examples(new AbsolutePathExamples(workingDirectory))
      .map { f =>
        toAbsolutePath(Paths.get(f), workingDirectory).toString
      }
      .filter(f => Files.exists(Paths.get(f)), x => x)
  }

  private val namedRule: P = {
    val termWidth = JLineAccess.terminalWidth
    token(
      NotQuoted,
      TokenCompletions.fixed(
        (seen, _) => {
          val candidates = loadedRules.filter(_.name.startsWith(seen))
          val maxRuleNameLen =
            candidates.map(_.name.length).reduceOption(_ max _).getOrElse(0)
          val rules = candidates
            .map { candidate =>
              val spaces = " " * (maxRuleNameLen - candidate.name.length)
              new Token(
                display =
                  s"${candidate.name}$spaces -- ${candidate.description}"
                    .take(termWidth),
                append = candidate.name.stripPrefix(seen)
              )
            }
            .toSet[Completion]
          Completions.strict(rules)
        }
      )
    )
  }
  private val gitDiffParser: P = {
    val jgitCompletion = new JGitCompletion(workingDirectory)
    token(
      NotQuoted,
      TokenCompletions.fixed(
        (seen, level) => {
          val last20Commits =
            jgitCompletion.last20Commits
              .filter { case (_, sha1) => sha1.startsWith(seen) }
              .zipWithIndex
              .map {
                case ((log, sha1), i) => {
                  val j = i + 1
                  val idx = if (j < 10) " " + j.toString else j.toString
                  new Token(
                    display = s"|$idx| $log",
                    append = sha1.stripPrefix(seen)
                  )
                }
              }
              .toSet

          val branchesAndTags =
            jgitCompletion.branchesAndTags
              .filter(info => info.startsWith(seen))
              .map { info =>
                new Token(display = info, append = info.stripPrefix(seen))
              }
              .toSet

          Completions.strict(last20Commits ++ branchesAndTags)
        }
      )
    )
  }

  def hide(p: P): P = p.examples()

  def parser: Parser[Seq[String]] = {
    val pathParser: P = token(filepathParser)
    val pathRegexParser: P = mapOrFail(pathParser) { regex =>
      Pattern.compile(regex); regex
    }
    val classpathParser: P = repsep(pathParser, File.pathSeparator)
    val fileRule: P = (token("file:") ~ pathParser.map("file:" + _)).map {
      case a ~ b => a + b
    }
    val ruleParser =
      namedRule | fileRule | uri("github") | uri("replace") |
        uri("http") | uri("https") | uri("scala")

    val classpath: P = arg("--classpath", classpathParser)
    val autoClasspath: P = "--auto-classpath"
    val config: P = arg("--config", "-c", pathParser)
    val diff: P = "--diff"
    val diffBase: P = arg("--diff-base", gitDiffParser)
    val exclude: P = arg("--exclude", pathRegexParser)
    val files: P = arg("--files", "-f", pathParser)
    val nonInteractive: P = "--non-interactive"
    val outFrom: P = arg("--out-from", pathRegexParser)
    val outTo: P = arg("--out-to", pathRegexParser)
    val rules: P = arg("--rules", "-r", ruleParser)
    val sourceroot: P = arg("--sourceroot", pathParser)
    val stdout: P = "--stdout"
    val test: P = "--test"
    val toolClasspath: P = arg("--tool-classpath", classpathParser)
    val help: P = "--help"
    val version: P = "--version" | hide("-v")
    val verbose: P = "--verbose"

    val base =
      classpath |
        autoClasspath |
        config |
        diff |
        diffBase |
        exclude |
        files |
        nonInteractive |
        outFrom |
        outTo |
        rules |
        sourceroot |
        stdout |
        test |
        toolClasspath |
        help |
        version |
        verbose

    val ruleDirect = ruleParser.map(rule => Seq("--rules", rule))
    val fileDirect = filepathParser.map(file => Seq("--files", file))

    ((token(Space) ~> base).* ~ (token(Space) ~> (fileDirect | ruleDirect)).?)
      .map {
        case a ~ b =>
          (a ++ b.getOrElse(Seq())).flatMap(_.split(" ").toSeq)
      }
  }
}
