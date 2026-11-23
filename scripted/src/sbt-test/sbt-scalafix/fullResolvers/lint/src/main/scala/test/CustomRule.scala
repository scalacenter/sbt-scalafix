package test

import scala.meta._

import scalafix.v1._

class CustomRule extends SyntacticRule("CustomRule") {
  override def fix(implicit doc: SyntacticDocument): Patch = {
    Patch.lint(
      Diagnostic(
        "lint-rule",
        "the lint copy of CustomRule was resolved instead of the rewrite one",
        doc.tree.pos
      )
    )
  }
}
