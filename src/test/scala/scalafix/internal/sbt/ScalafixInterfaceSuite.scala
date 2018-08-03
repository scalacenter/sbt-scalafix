package scalafix.internal.sbt

import org.scalatest.FunSuite
import scalafix.sbt.ScalafixInterface

class ScalafixInterfaceSuite extends FunSuite {
  test("classloadInstance") {
    val cli = ScalafixInterface.classloadInstance()
    val e = intercept[Exception] {
      cli.main(Array("--foobar", "--no-sys-exit"))
    }
    assert(e.getMessage.contains("CommandLineError"))
  }
}
