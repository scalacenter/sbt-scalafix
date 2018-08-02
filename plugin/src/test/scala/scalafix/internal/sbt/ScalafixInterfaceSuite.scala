package scalafix.internal.sbt
import org.scalatest.FunSuite
import scalafix.sbt.ScalafixInterface

class ScalafixInterfaceSuite extends FunSuite {
  test("load") {
    val cli = ScalafixInterface.classloadInstance()
    cli.main(Array("--help", "--no-sys-exit"))
  }

}
