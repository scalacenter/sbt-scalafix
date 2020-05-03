package local

import scalafix.v1._

class Syntactic extends SyntacticRule("LocalSyntacticRule") {
  override def fix(implicit doc: SyntacticDocument): Patch = {
    new org.joda.time.Instant() // let's pretent we need this for the rule logic
    Patch.empty
  }
}
