package fix

import scalafix.testkit._
import scala.util.control.NonFatal
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.exceptions.TestFailedException

object IntputOutputSuite {
  def main(args: Array[String]): Unit = {
    if (Array("--save-expect").sameElements(args)) {
      val suite = new AbstractSemanticRuleSuite with AnyFunSuiteLike {
        override val props = TestkitProperties.loadFromResources()
        override val isSaveExpect = true

        testsToRun.foreach { t =>
          try evaluateTestBody(t)
          catch {
            case _: TestFailedException =>
            case NonFatal(e) =>
              e.printStackTrace()
          }
        }
      }
      println("Promoted expect tests")
    } else {
      println(
        s"unknown arguments '${args.mkString(" ")}', expected '--save-expect'"
      )
    }
  }
}

class IntputOutputSuite extends AbstractSemanticRuleSuite with AnyFunSuiteLike {
  runAllTests()
}
