package tests

import org.scalatest.funsuite.AnyFunSuite
import scala.meta.internal.io._
import scala.meta.testkit.StringFS

class CheckSuite extends AnyFunSuite {

  test("> scalafix") {
    val root = PathIO.workingDirectory.resolve("example").resolve("src")
    val obtained = StringFS.asString(root)
    val expected =
      """
        |/main/scala/example/Example.scala
        |package example
        |
        |import java.util.Map
        |
        |object Example {
        |  implicit val str: Map.Entry[Int,String] = null.asInstanceOf[Map.Entry[Int, String]]
        |}
        |// Hello world!
        |""".stripMargin
    munit.Assertions.assertNoDiff(obtained, expected)
  }

}
