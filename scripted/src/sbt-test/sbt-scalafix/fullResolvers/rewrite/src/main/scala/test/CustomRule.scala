package test

import scala.meta._

import scalafix.v1._

class CustomRule extends SyntacticRule("CustomRule") {
  override def fix(implicit doc: SyntacticDocument): Patch = {
    doc.tree.collect { case t: Term.Name =>
      Patch.replaceTree(t, "Foo")
    }.asPatch
  }
}
