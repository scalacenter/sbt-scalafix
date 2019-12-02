package scalafix.internal.sbt

import scalafix.interfaces.ScalafixRule

import java.io.File
import java.nio.file._

import sbt.complete._
import sbt.complete.DefaultParsers._

class ScalafixCompletions(
    workingDirectory: () => Path,
    loadedRules: () => Seq[ScalafixRule],
    terminalWidth: Option[Int]
) {

  private type P = Parser[String]

  private val equal: P = token("=").map(_.toString)
  private val string: P = StringBasic
  private def arg(key: String, value: P): P =
    (key ~ equal ~ value).map { case a ~ _ ~ c => a + "=" + c }
  private def arg(key: String, shortKey: String, value: P): P =
    ((key | shortKey).examples(key) ~ equal ~ value).map {
      case a ~ _ ~ c => a + "=" + c
    }

  private def uri(protocol: String): Parser[Rule] =
    token(protocol + ":") ~> NotQuoted.map(x => Rule(s"$protocol:$x"))

  private val filepathParser: Parser[Path] = {
    def toAbsolutePath(path: Path, cwd: Path): Path = {
      if (path.isAbsolute) path
      else cwd.resolve(path)
    }.normalize()

    // Extend FileExamples to tab complete when the prefix is an absolute path or `..`
    class AbsolutePathExamples(cwd: Path, prefix: String = "")
        extends FileExamples(cwd.toFile, prefix) {
      override def withAddedPrefix(addedPrefix: String): FileExamples = {

        val nextPrefix =
          if (addedPrefix.startsWith(".")) addedPrefix
          else prefix + addedPrefix
        val (b, p) = AbsolutePathCompleter.mkBase(nextPrefix, cwd)
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
      .examples(new AbsolutePathExamples(workingDirectory()))
      .map { f =>
        toAbsolutePath(Paths.get(f), workingDirectory())
      }
      .filter(f => Files.exists(f), x => x)
  }

  private val namedRule: P = {
    token(
      string,
      TokenCompletions.fixed((seen, _) => {
        val candidates = loadedRules().iterator
          .filterNot(_.isExperimental)
          .filter(_.name.startsWith(seen))
        val rules = candidates
          .map { candidate =>
            val output = s"${candidate.name}\n  ${candidate.description}"
            new Token(
              display = terminalWidth.map(output.take).getOrElse(output).trim,
              append = candidate.name.stripPrefix(seen)
            )
          }
          .toSet[Completion]
        Completions.strict(rules)
      })
    ).filter(!_.startsWith("-"), x => x)
  }
  private val namedRule2: Parser[Rule] =
    namedRule.map(s => Rule(s))
  private lazy val gitDiffParser: P = {
    val jgitCompletion = new JGitCompletion(workingDirectory())
    token(
      NotQuoted,
      TokenCompletions.fixed((seen, _) => {
        val last20Commits =
          jgitCompletion.last20Commits
            .filter { case (_, sha1) => sha1.startsWith(seen) }
            .zipWithIndex
            .map {
              case ((log, sha1), i) =>
                val j = i + 1
                val idx = if (j < 10) " " + j.toString else j.toString
                new Token(
                  display = s"|$idx| $log",
                  append = sha1.stripPrefix(seen)
                )
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
      })
    )
  }

  def hide(p: P): P = p.examples()

  def parser: Parser[ShellArgs] = {
    val pathParser: Parser[Path] = token(filepathParser)
    val fileRule: Parser[Rule] =
      (token("file:") ~ pathParser).map {
        case _ ~ path => Rule(path.toUri.toString)
      }

    val diff: P = "--diff"
    val diffBase: P = arg("--diff-base", gitDiffParser)
    val extra: P = hide(string)
    val files: P = arg("--files", "-f", pathParser.map(_.toString))
    val help: P = "--help"
    val verbose: P = "--verbose"
    val autoSuppressLinterErrors: P = "--auto-suppress-linter-errors"
    val base =
      help |
        verbose |
        files |
        diff |
        diffBase |
        autoSuppressLinterErrors |
        extra
    val shellBase = base.map(Extra)
    val rules: Parser[Rule] =
      fileRule |
        uri("class") |
        uri("replace") |
        uri("github") |
        uri("dependency") |
        namedRule2
    val shellArg: Parser[ShellArg] =
      rules | shellBase
    (token(Space) ~> shellArg).*.map { args =>
      ShellArgs(args)
    }
  }
}
