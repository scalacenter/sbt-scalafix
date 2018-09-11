package scalafix.internal.sbt

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import org.scalactic.source.Position
import org.scalatest.FunSuite
import sbt._
import sbt.internal.sbtscalafix.Compat
import scala.collection.JavaConverters._
import scalafix.interfaces.ScalafixError

class ScalafixAPISuite extends FunSuite {

  def assertNoDiff(obtained: String, expected: String)(
      implicit pos: Position
  ): Unit = {
    def strip(s: String) = s.trim.replaceAll("\r\n", "\n")
    val obtainedStrip = strip(obtained)
    val expectedStrip = strip(expected)
    assert(obtainedStrip == expectedStrip)
  }

  test("ScalafixPlugin.cli") {
    val baos = new ByteArrayOutputStream()
    val logger = Compat.ConsoleLogger(new PrintStream(baos))
    val ScalafixInterface(_, args) = ScalafixInterface.fromToolClasspath(
      List("com.geirsson" %% "example-scalafix-rule" % "1.3.0"),
      logger
    )()
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
    val availableRules = args.availableRules().asScala.toList.map(_.name())
    assert(availableRules.contains("SyntacticRule"))
    val mainArgs = args
      .withPrintStream(new PrintStream(baos))
      .withPaths(List(tmp).asJava)
      .withRules(
        List(
          "SyntacticRule", // from example-scalafix-rule
          "ProcedureSyntax",
          "DisableSyntax"
        ).asJava
      )
      .withParsedArguments(
        List(
          "--settings.DisableSyntax.noSemicolons",
          "true"
        ).asJava
      )
    val obtainedError = mainArgs.run().toList
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
