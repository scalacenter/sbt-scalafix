package scalafix.internal.sbt

import scalafix.interfaces.ScalafixRule

import java.io.File
import java.nio.file._
import java.util.regex.Pattern

import scala.util.control.NonFatal

import sbt.complete._
import sbt.complete.DefaultParsers._

class ScalafixCompletions(
    workingDirectory: Path,
    loadedRules: List[ScalafixRule],
    terminalWidth: Option[Int]
) {

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
    token(
      string,
      TokenCompletions.fixed(
        (seen, _) => {
          val candidates = loadedRules.filter(_.name.startsWith(seen))
          val maxRuleNameLen =
            candidates.map(_.name.length).reduceOption(_ max _).getOrElse(0)
          val rules = candidates
            .map { candidate =>
              val spaces = " " * (maxRuleNameLen - candidate.name.length)

              val output =
                s"${candidate.name}$spaces -- ${candidate.description}"

              new Token(
                display = terminalWidth.map(output.take).getOrElse(output),
                append = candidate.name.stripPrefix(seen)
              )
            }
            .toSet[Completion]
          Completions.strict(rules)
        }
      )
    ).filter(!_.startsWith("-"), x => x)
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
    val fileRule: P = (token("file:") ~ pathParser.map("file:" + _)).map {
      case a ~ b => a + b
    }
    val ruleParser =
      namedRule |
        fileRule |
        uri("github") |
        uri("replace") |
        uri("http") |
        uri("https") |
        uri("scala")

    val autoSuppressLinterErrors: P = "--auto-suppress-linter-errors"
    val diff: P = "--diff"
    val diffBase: P = arg("--diff-base", gitDiffParser)
    val extra: P = hide(string)
    val files: P = arg("--files", "-f", pathParser)
    val help: P = "--help"
    val ruleDirect: P = namedRule.map(rule => s"--rules $rule")
    val rules: P = arg("--rules", "-r", ruleParser)
    val test: P = "--test"
    val verbose: P = "--verbose"
    val base =
      autoSuppressLinterErrors |
        diff |
        diffBase |
        files |
        help |
        ruleDirect |
        rules |
        test |
        verbose |
        extra
    (token(Space) ~> base).*.map(_.flatMap(_.split(" ").toSeq))
  }
}
