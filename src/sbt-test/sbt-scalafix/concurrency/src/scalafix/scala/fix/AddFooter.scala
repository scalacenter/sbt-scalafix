package fix

import scalafix.v1._

class AddFooter extends SyntacticRule("AddFooter") {
  override def fix(implicit doc: SyntacticDocument): Patch = {
    // artificial delay between read/parsing and write/patching to exercise concurrency
    Thread.sleep(1000)
    Patch.addRight(doc.tree, "\n// FOOTER\n")
  }
}
