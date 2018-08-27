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
    loadedRules = loadedRules
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
    val obtained = displays.toSet
    val expected = Set(
      "",
      "--classpath",
      "--config",
      "--diff",
      "--diff-base",
      "--exclude",
      "--help",
      "--non-interactive",
      "--out-from",
      "--out-to",
      "--rules",
      "--sourceroot",
      "--stdout",
      "--test",
      "--tool-classpath",
      "--verbose",
      "--version"
    )

    val diff = expected -- obtained

    assert(diff.isEmpty)
  }

  checkCompletion("--classpath ba", SkipWindows) { (appends, displays) =>
    assert(displays.contains("bar"))
    assert(appends.contains("r"))
  }
  checkCompletion("--classpath bar", SkipWindows) { (appends, displays) =>
    assert(displays.contains(":"))
    assert(appends.contains(":"))
  }
  checkCompletion("--classpath bar:", SkipWindows) { (appends, displays) =>
    assert(displays.contains("foo"))
    assert(appends.contains("foo"))
  }
  checkCompletion("--config ", SkipWindows) { (appends, displays) =>
    assert(displays.contains("my.conf"))
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
  checkCompletion("--exclude" + space, SkipWindows) { (appends, displays) =>
    assert(displays.contains("foo"))
    assert(appends.contains("foo"))
  }
  checkCompletion("--out-from" + space, SkipWindows) { (appends, displays) =>
    assert(displays.contains("foo"))
    assert(appends.contains("foo"))
  }
  checkCompletion("--out-to" + space, SkipWindows) { (appends, displays) =>
    assert(displays.contains("foo"))
    assert(appends.contains("foo"))
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

  checkCompletion("", SkipWindows) { (_, displays) =>
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

  // shortcut for --rules
  checkArgs("ProcedureSyntax") { args =>
    assert(args == Seq("--rules", "ProcedureSyntax"))
  }

  // shortcut for --files
  checkArgs("foo") { args =>
    val List(argName, arg) = args.toList

    assert(argName == "--files")

    val filename = Paths.get(arg).getFileName.toString
    assert(filename == "foo")
  }

  checkCompletion("--rules file:bar/../", SkipWindows) { (appends, displays) =>
    // resolve parent directories
    assert(appends.contains("foo"))
    assert(displays.contains("bar/../foo"))
  }
  checkCompletion("--sourceroot" + space, SkipWindows) { (appends, displays) =>
    assert(displays.contains("bar"))
    assert(appends.contains("bar"))
  }
  checkCompletion("--tool-classpath ba", SkipWindows) { (appends, displays) =>
    assert(displays.contains("bar"))
    assert(appends.contains("r"))
  }
  checkCompletion("--tool-classpath bar", SkipWindows) { (appends, displays) =>
    assert(displays.contains(":"))
    assert(appends.contains(":"))
  }
  checkCompletion("--tool-classpath bar:", SkipWindows) { (appends, displays) =>
    assert(displays.contains("buzz"))
    assert(appends.contains("buzz"))
  }
}
