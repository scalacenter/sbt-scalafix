package scalafix.internal.sbt

import sbt.internal.sbtscalafix.JLineAccess

import org.scalatest.FunSuite
import sbt.complete.Parser
import sbt.complete.Completion
import sbt.internal.sbtscalafix.JLineAccess

import org.eclipse.jgit.lib.AbbreviatedObjectId
import org.scalatest.Tag

import sbt.internal.sbtscalafix.Compat.ConsoleLogger

import scala.collection.JavaConverters._

import java.nio.file.Paths

class SbtCompletionsSuite extends FunSuite {
  val fs = new Fs()
  val git = new Git(fs.workingDirectory)
  fs.mkdir("foo")
  fs.add("foo/f.scala", "object F")
  fs.mkdir("bar")
  fs.add("bar/b.scala", "object B")
  fs.mkdir("buzz")
  fs.add("my.conf", "conf.file = []")
  git.commit()
  // create at least 20 commits for diff-base log
  (1 to 20).foreach { i =>
    fs.add(s"f$i", s"f$i")
    git.commit()
  }
  git.tag("v0.1.0")

  val exampleDependency =
    sbt.ModuleID("com.geirsson", "example-scalafix-rule_2.12", "1.2.0")
  val (_, mainArgs) = scalafix.sbt.ScalafixPlugin
    .classloadScalafixAPI(ConsoleLogger(), Seq(exampleDependency))
  val loadedRules = mainArgs.availableRules.asScala.toList

  val parser = new ScalafixCompletions(
    workingDirectory = fs.workingDirectory.toAbsolutePath,
    loadedRules = loadedRules,
    terminalWidth = None
  ).parser
  val space = " "

  def checkCompletion(name: String, testTags: Tag*)(
      assertCompletions: (List[String], List[String]) => Unit
  ): Unit = {
    val option =
      if (name == "all") ""
      else name

    test(name, testTags: _*) {
      val input = " " + option

      val completions =
        Parser.completions(parser, input, 0).get.toList.sortBy(_.display)

      val appends = completions.map(_.append)
      val displays = completions.map(_.display)

      assertCompletions(appends, displays)
    }
  }

  def checkArgs(name: String)(assertArgs: Seq[String] => Unit): Unit = {
    test(name) {
      val input = name
      val args = Parser.parse(" " + input, parser).right.get
      assertArgs(args)
    }
  }

  def isSha1(in: String): Boolean =
    AbbreviatedObjectId.isId(in)

  checkCompletion("all", SkipWindows) { (_, displays) =>
    val obtained = displays
    val expected = List(
      "",
      " ",
      "--auto-suppress-linter-errors",
      "--diff",
      "--diff-base",
      "--files",
      "--help",
      "--rules",
      "--verbose",
      "DisableSyntax           -- Linter that reports an error on a configurable set of keywords and syntax.",
      "ExplicitResultTypes     -- Rewrite that inserts explicit type annotations for def/val/var",
      "LeakingImplicitClassVal -- Add private access modifier to val parameters of implicit value classes in order to prevent public access",
      "NoAutoTupling           -- Rewrite that inserts explicit tuples for adapted argument lists for compatibility with -Yno-adapted-args",
      "NoValInForComprehension -- Rewrite that removes redundant val inside for-comprehensions",
      "ProcedureSyntax         -- Rewrite that inserts explicit : Unit = for soon-to-be-deprecated procedure syntax def foo { ... }",
      "RemoveUnusedImports     -- Rewrite that removes unused imports reported by the compiler under -Xwarn-unused-import.",
      "RemoveUnusedTerms       -- Rewrite that removes unused locals or privates by -Ywarn-unused:locals,privates",
      "SemanticRule            -- ",
      "SyntacticRule           -- "
    )
    assert(obtained == expected)
  }

  checkCompletion("--diff-base" + space, SkipWindows) { (appends, displays) =>
    // branches
    assert(displays.contains("master"))

    // tag
    assert(displays.contains("v0.1.0"))

    // last 20 commits
    appends.zip(displays).reverse.take(20).foreach {
      case (append, display) =>
        assert(isSha1(append))
        assert(display.endsWith("ago)"))
    }
  }
  checkCompletion("--rules" + space, SkipWindows) { (_, displays) =>
    // built-in
    assert(displays.exists(_.startsWith("ProcedureSyntax")))

    // from classpath
    assert(displays.exists(_.startsWith("SemanticRule")))

    // protocols
    assert(displays.contains("file:"))
    assert(displays.contains("github:"))
    assert(displays.contains("http:"))
    assert(displays.contains("https:"))
    assert(displays.contains("replace:"))
    assert(displays.contains("scala:"))
  }

  checkCompletion("--rules file:bar/../", SkipWindows) { (appends, displays) =>
    // resolve parent directories
    assert(appends.contains("foo"))
    assert(displays.contains("bar/../foo"))
  }

  // shortcut for --rules
  checkArgs("ProcedureSyntax") { args =>
    assert(args == Seq("--rules", "ProcedureSyntax"))
  }

  // consume extra
  checkArgs("--bash") { args =>
    assert(args == Seq("--bash"))
  }
}
