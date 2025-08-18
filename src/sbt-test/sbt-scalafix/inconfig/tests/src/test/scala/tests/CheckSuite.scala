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
        |/custom/scala/example/ExampleIT.scala
        |package example
        |
        |object ExampleCustom {
        |  implicit val str: java.util.Map.Entry[Int,String] = null.asInstanceOf[java.util.Map.Entry[Int, String]]
        |}
        |// Hello world!
        |
        |/main/scala/example/Example.scala
        |package example
        |
        |object Example {
        |  implicit val str = null.asInstanceOf[java.util.Map.Entry[Int, String]]
        |}
        |""".stripMargin
    munit.Assertions.assertNoDiff(obtained, expected)
  }

}
