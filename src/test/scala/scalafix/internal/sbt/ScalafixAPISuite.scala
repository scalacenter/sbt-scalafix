package scalafix.internal.sbt

import java.nio.file.Files
import org.scalatest.FunSuite
import scalafix.sbt.ScalafixPlugin
import scala.collection.JavaConverters._
import sbt._

class ScalafixAPISuite extends FunSuite {
  test("ScalafixPlugin.cli") {
    val (api, args) = ScalafixPlugin.classloadScalafixAPI(
      List(
        "com.geirsson" % "example-scalafix-rule_2.12" % "1.2.0"
      )
    )
    val tmp = Files.createTempFile("scalafix", "Tmp.scala")
    tmp.toFile.deleteOnExit()
    Files.write(
      tmp,
      """
        |object A {}
        |""".stripMargin.getBytes()
    )
    val exit = api
      .runMain(
        args
          .withPaths(List(tmp).asJava)
          .withRules(List("SyntacticRule").asJava) // from example-scalafix-rule
      )
      .toList
    assert(exit.isEmpty)
    val obtained = new String(Files.readAllBytes(tmp))
    assert(obtained.endsWith("// v1 SyntacticRule!\n"))
  }
}
