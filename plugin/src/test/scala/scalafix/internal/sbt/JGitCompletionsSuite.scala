package scalafix.internal.sbt

import org.eclipse.jgit.api.Git as JGit
import org.scalatest.funsuite.AnyFunSuite

class JGitCompletionsSuite extends AnyFunSuite {

  test("directory with no .git") {
    val fs = new Fs()
    val jgit = new JGitCompletion(fs.workingDirectory)
    assert(jgit.last20Commits == Nil)
  }

  test("directory with empty .git") {
    val fs = new Fs()
    fs.mkdir(".git")
    val jgit = new JGitCompletion(fs.workingDirectory)
    assert(jgit.last20Commits == Nil)
  }

  test("directory with no commits") {
    val fs = new Fs()
    JGit.init().setDirectory(fs.workingDirectory.toFile).call() // git init
    val jgit = new JGitCompletion(fs.workingDirectory)
    assert(jgit.last20Commits == Nil)
  }

}
