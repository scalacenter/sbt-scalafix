package test

import scala.meta._

import scalafix.v1._

class RenameToFoo extends SyntacticRule("RenameToFoo") {
  override def fix(implicit doc: SyntacticDocument): Patch = {
    doc.tree.collect { case t: Term.Name =>
      Patch.replaceTree(t, "Foo")
    }.asPatch
  }
}
