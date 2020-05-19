package local

import scalafix.v1._

class Syntactic extends SyntacticRule("LocalSyntacticRule") {
  override def fix(implicit doc: SyntacticDocument): Patch = {
    Patch.empty
  }
}
