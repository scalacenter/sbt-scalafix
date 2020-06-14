package scalafix.internal.sbt

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.file.Files

import coursierapi.Repository
import org.scalactic.source.Position
import sbt._
import sbt.internal.sbtscalafix.Compat
import scalafix.interfaces.ScalafixError
import org.scalatest.funsuite.AnyFunSuite

import scala.util.Properties

class ScalafixAPISuite extends AnyFunSuite {

  def assertNoDiff(obtained: String, expected: String)(implicit
      pos: Position
  ): Unit = {
    def strip(s: String) = s.trim.replaceAll("\r\n", "\n")
    val obtainedStrip = strip(obtained)
    val expectedStrip = strip(expected)
    assert(obtainedStrip == expectedStrip)
  }

  test("ScalafixPlugin.cli") {
    assume(!Properties.isWin)
    val baos = new ByteArrayOutputStream()
    val logger = Compat.ConsoleLogger(new PrintStream(baos))
    val interface = ScalafixInterface
      .fromToolClasspath(
        List("ch.epfl.scala" %% "example-scalafix-rule" % "1.4.0"),
        Seq(Repository.central),
        logger
      )()
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
    val out = fansi.Str(baos.toString()).plainText
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
