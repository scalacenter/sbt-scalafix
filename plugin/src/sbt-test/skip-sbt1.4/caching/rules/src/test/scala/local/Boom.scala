package local

import scala.meta.Position

import scalafix.lint.LintSeverity
import scalafix.v1._

// This is meant to be dropped-in in src/main to be usable
class Syntactic extends SyntacticRule("LocalSyntacticRule") {
  override def fix(implicit doc: SyntacticDocument): Patch = {
    Patch.lint(
      Diagnostic(
        "",
        "expected lint error",
        Position.None,
        "",
        LintSeverity.Error
      )
    )
  }
}
