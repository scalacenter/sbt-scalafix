package sameproject

import scalafix.v1._

class Syntactic extends SyntacticRule("SameProjectSyntacticRule") {
  override def fix(implicit doc: SyntacticDocument): Patch = {
    Patch.empty
  }
}
