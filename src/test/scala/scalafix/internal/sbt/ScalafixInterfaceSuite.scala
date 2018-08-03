package scalafix.internal.sbt

import org.scalatest.FunSuite
import scalafix.sbt.ScalafixPlugin

class ScalafixInterfaceSuite extends FunSuite {
  test("ScalafixPlugin.cli") {
    val Right(cli) = ScalafixPlugin.cli
    val e = intercept[Exception] {
      cli.main(Array("--foobar", "--no-sys-exit"))
    }
    assert(e.getMessage.contains("CommandLineError"))
  }
}
