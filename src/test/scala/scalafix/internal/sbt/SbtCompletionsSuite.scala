package scalafix.internal.sbt

import coursierapi.{MavenRepository, Repository}
import org.eclipse.jgit.lib.AbbreviatedObjectId
import org.scalatest.Tag
import sbt.complete.Parser
import org.scalatest.funsuite.AnyFunSuite

class SbtCompletionsSuite extends AnyFunSuite {
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

  val exampleDependency = {
    import sbt._
    "ch.epfl.scala" %% "example-scalafix-rule" % "2.0.0-RC1"
  }
  val mainArgs =
    ScalafixInterface(
      new BlockingCache(),
      "2.12",
      Arg.ToolClasspath(
        Nil,
        Seq(exampleDependency),
        Seq(
          Repository.central,
          MavenRepository.of(
            "https://oss.sonatype.org/content/repositories/snapshots"
          )
        )
      ),
      ScalafixInterface.defaultLogger,
      new ScalafixLogger(ScalafixInterface.defaultLogger)
    )
  val loadedRules = mainArgs.availableRules.toList

  val defaultParser = new ScalafixCompletions(
    workingDirectory = fs.workingDirectory.toAbsolutePath,
    loadedRules = () => loadedRules,
    terminalWidth = None,
    allowedTargetFilesPrefixes = Seq(fs.workingDirectory.resolve("foo")),
    jgitCompletion = new JGitCompletion(fs.workingDirectory.toAbsolutePath)
  ).parser

  def checkCompletion(name: String, testTags: Tag*)(
      assertCompletions: (List[String], List[String]) => Unit
  ): Unit = {
    val option =
      if (name == "all") ""
      else name

    test(name, testTags: _*) {
      val input = " " + option

      val completions =
        Parser.completions(defaultParser, input, 0).get.toList.sortBy(_.display)

      val appends = completions.map(_.append)
      val displays = completions.map(_.display)

      assertCompletions(appends, displays)
    }
  }

  def checkArgs(
      name: String,
      testTags: Tag*
  )(assertArgs: Either[String, ShellArgs] => Unit): Unit =
    checkArgs(defaultParser)(name, testTags*)(assertArgs)

  def checkArgs(
      parser: Parser[ShellArgs]
  )(
      name: String,
      testTags: Tag*
  )(assertArgs: Either[String, ShellArgs] => Unit): Unit = {
    test(name, testTags: _*) {
      val input = name
      val args = Parser.parse(" " + input, parser)
      assertArgs(args)
    }
  }

  def isSha1(in: String): Boolean =
    AbbreviatedObjectId.isId(in)

  checkCompletion("all", SkipWindows) { (_, displays) =>
    val obtained = displays.mkString("\n").trim
    val expected =
      """|
        |--auto-suppress-linter-errors
        |--check
        |--diff
        |--diff-base=
        |--files=
        |--help
        |--no-cache
        |--rules=
        |--stdout
        |--syntactic
        |--verbose
        |--version
        |DisableSyntax
        |  Reports an error for disabled features such as var or XML literals.
        |ExplicitResultTypes
        |  Inserts type annotations for inferred public members.
        |LeakingImplicitClassVal
        |  Adds 'private' to val parameters of implicit value classes
        |NoAutoTupling
        |  Inserts explicit tuples for adapted argument lists for compatibility with -Yno-adapted-args
        |NoValInForComprehension
        |  Removes deprecated val inside for-comprehension binders
        |OrganizeImports
        |  Organize import statements
        |ProcedureSyntax
        |  Replaces deprecated Scala 2.x procedure syntax with explicit ': Unit ='
        |RedundantSyntax
        |  Removes redundant syntax such as `final` modifiers on an object
        |RemoveUnused
        |  Removes unused imports and terms that reported by the compiler under -Wunused
        |SemanticRule
        |SyntacticRule
        |class:
        |dependency:
        |file:
        |github:
        |replace:
        |""".stripMargin.trim.replaceAll("\r\n", "\n")
    if (obtained != expected) {
      println("\"\"\"|")
      obtained.linesIterator.foreach { line =>
        print("   |")
        println(line)
      }
      println("   |\"\"\"")
      fail("obtained != expected")
    }
  }

  checkCompletion("--fil") { (appends, _) =>
    assert(appends == Seq("es="))
  }

  checkCompletion("--files=") { (appends, _) =>
    // allowedTargetFilesPrefixes affects `--files=` completion since bar and buzz are missing
    assert(!appends.contains("bar"))
    assert(!appends.contains("buzz"))
  }

  // only provide values suggestion after the key of a key/value arg
  checkCompletion("--diff-base ") { (appends, _) =>
    assert(appends.nonEmpty)
    assert(!appends.contains("--help"))
  }

  checkCompletion("--diff-base=", SkipWindows) { (appends, displays) =>
    // branches
    assert(displays.contains("master"))

    // tag
    assert(displays.contains("v0.1.0"))

    // last 20 commits
    appends.zip(displays).reverse.take(20).foreach { case (append, display) =>
      assert(isSha1(append))
      assert(display.endsWith("ago)"))
    }
  }

  checkCompletion("-r=") { (appends, _) =>
    assert(appends.contains("DisableSyntax"))
    assert(appends.contains("class:"))
    assert(!appends.contains("--help"))
  }

  checkCompletion("--rules file:bar/../", SkipWindows) { (appends, displays) =>
    // resolve parent directories
    // allowedTargetFilesPrefixes does not affect `file:` completion as buzz is not part of them
    assert(appends.contains("buzz"))
    assert(displays.contains("bar/../buzz"))
  }

  // shortcut for --rules
  checkArgs("ProcedureSyntax") { args =>
    assert(args == Right(ShellArgs(rules = List("ProcedureSyntax"))))
  }

  // consume extra
  checkArgs("--bash") { args =>
    assert(args == Right(ShellArgs(extra = List("--bash"))))
  }

  checkArgs("--files --test") { args =>
    assert(
      args == Left(
        """--files value(s) must reference existing files or directories in unmanagedSourceDirectories; are you running scalafix on the right project / Configuration?
          | --files --test
          |               ^""".stripMargin
      )
    )
  }

  checkArgs("--test --rules=Foo --files=NotHere", SkipWindows) { args =>
    assert(args == Left("""--files=NotHere
      |--files value(s) must reference existing files or directories in unmanagedSourceDirectories; are you running scalafix on the right project / Configuration?
      | --test --rules=Foo --files=NotHere
      |                                   ^""".stripMargin))
  }

  checkArgs("--test  -f= --rules=Foo", SkipWindows) { args =>
    assert(args == Left("""Expected non-whitespace character
      |--files value(s) must reference existing files or directories in unmanagedSourceDirectories; are you running scalafix on the right project / Configuration?
      | --test  -f= --rules=Foo
      |            ^""".stripMargin))
  }

  checkArgs("--test  -f --rules=Foo", SkipWindows) { args =>
    assert(
      args == Left(
        """--files value(s) must reference existing files or directories in unmanagedSourceDirectories; are you running scalafix on the right project / Configuration?
          | --test  -f --rules=Foo
          |                       ^""".stripMargin
      )
    )
  }

  checkArgs("--test --rules=Foo -f", SkipWindows) { args =>
    assert(args == Left("""-f
      |Expected '='
      |Expected ' '
      | --test --rules=Foo -f
      |                      ^""".stripMargin))
  }

  checkArgs("--test -f foo/f.scala", SkipWindows) { args =>
    assert(
      args == Right(
        ShellArgs(
          files = fs.workingDirectory.toAbsolutePath
            .resolve("foo/f.scala")
            .toString :: Nil,
          extra = "--test" :: Nil
        )
      )
    )
  }

  checkArgs("--test -f bar/b.scala", SkipWindows) { args =>
    assert(
      args == Left(
        """--files value(s) must reference existing files or directories in unmanagedSourceDirectories; are you running scalafix on the right project / Configuration?
          | --test -f bar/b.scala
          |                      ^""".stripMargin
      )
    )
  }

  checkArgs(
    new ScalafixCompletions(
      workingDirectory = fs.workingDirectory.toAbsolutePath,
      loadedRules = () => Nil,
      terminalWidth = None,
      allowedTargetFilesPrefixes = Nil,
      jgitCompletion = new JGitCompletion(fs.workingDirectory.toAbsolutePath)
    ).parser
  )("--test --files=foo", SkipWindows) { args =>
    assert(args == Left("""--files=foo
      |--files can only be used on project-level invocations (i.e. myproject / scalafix --files=f.scala)
      | --test --files=foo
      |                   ^""".stripMargin))
  }

}
