package scalafix.internal.sbt

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files

import scala.util.Properties

import sbt.*

import coursierapi.MavenRepository
import coursierapi.Repository
import org.scalactic.source.Position
import org.scalatest.funsuite.AnyFunSuite
import scalafix.interfaces.ScalafixError

class ScalafixAPISuite extends AnyFunSuite {

  def assertNoDiff(obtained: String, expected: String)(implicit
      pos: Position
  ): Unit = {
    def removeSnapshotInfo(s: String) =
      s.replace(
        "[info] Using SNAPSHOT artifacts for Scalafix and/or external rules, binary compatibility checks disabled",
        ""
      )
    def strip(s: String) = s.trim.replaceAll("\r\n", "\n")
    val obtainedStrip = strip(removeSnapshotInfo(obtained))
    val expectedStrip = strip(expected)
    assert(obtainedStrip == expectedStrip)
  }

  test("ScalafixPlugin.cli") {
    assume(!Properties.isWin)
    val baos = new ByteArrayOutputStream()
    val logger = ConsoleLogger(new PrintStream(baos))
    val interface = ScalafixInterface(
      new BlockingCache(),
      "2.12",
      Arg.ToolClasspath(
        Nil,
        List("ch.epfl.scala" %% "example-scalafix-rule" % "6.0.0"),
        Seq(
          Repository.central,
          MavenRepository.of(
            "https://oss.sonatype.org/content/repositories/snapshots"
          )
        )
      ),
      logger,
      new ScalafixLogger(logger)
    )
      .withArgs(Arg.PrintStream(new PrintStream(baos)))
    val tmp = Files.createTempFile("scalafix", "Tmp.scala")
    tmp.toFile.deleteOnExit()
    Files.write(
      tmp,
      """
        |object A {
        |  val x = 1;
        |  def foo { println(42) }
        |}
        |""".stripMargin.getBytes()
    )
    val availableRules = interface.availableRules().toList.map(_.name())
    assert(availableRules.contains("SyntacticRule"))
    val mainInterface = interface.withArgs(
      Arg.Paths(List(tmp)),
      Arg.Rules(
        List(
          "SyntacticRule", // from example-scalafix-rule
          "ProcedureSyntax",
          "DisableSyntax"
        )
      ),
      Arg.ParsedArgs(
        List(
          "--settings.DisableSyntax.noSemicolons",
          "true"
        )
      )
    )
    val obtainedError = mainInterface.run().toList
    val out = fansi.Str(baos.toString(), fansi.ErrorMode.Strip).plainText
    assert(obtainedError == List(ScalafixError.LinterError), out)
    val obtained = new String(Files.readAllBytes(tmp))
    assert(obtained.contains(": Unit = {"), out)
    assert(obtained.endsWith("// v1 SyntacticRule!\n"), out)
    val obtainedOut = out.replaceFirst(".*Tmp.scala", "[error] Tmp.scala")
    assertNoDiff(
      obtainedOut,
      """|[error] Tmp.scala:3:12: error: [DisableSyntax.noSemicolons] semicolons are disabled
        |[error]   val x = 1;
        |[error]            ^
        |""".stripMargin
    )
  }
}
