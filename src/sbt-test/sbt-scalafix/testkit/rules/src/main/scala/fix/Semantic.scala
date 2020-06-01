package fix

import scalafix.v1._

class Semantic extends SemanticRule("SemanticRule") {
  override def fix(implicit doc: SemanticDocument): Patch = {
    if (doc.input.text.contains(s"// v1 $name!")) Patch.empty
    else Patch.addRight(doc.tree, s"// v1 $name!\n")
  }
}
