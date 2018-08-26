package tests

import org.scalatest._
import scala.meta.internal.io._
import scala.meta.testkit._

class CheckSuite extends FunSuite with DiffAssertions {

  test("> scalafix") {
    val root = PathIO.workingDirectory.resolve("example").resolve("src")
    val obtained = StringFS.asString(root)
    val expected =
      """
        |/it/scala/example/ExampleIT.scala
        |package example
        |
        |object ExampleIT {
        |  implicit val str: _root_.java.util.Map.Entry[_root_.scala.Int, _root_.scala.Predef.String] = null.asInstanceOf[java.util.Map.Entry[Int, String]]
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
    assertNoDiff(obtained, expected)
  }

}
