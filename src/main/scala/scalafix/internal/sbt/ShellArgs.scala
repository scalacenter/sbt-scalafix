package scalafix.internal.sbt

object ShellArgs {
  def apply(args: Seq[ShellArg]): ShellArgs = {
    val rules = List.newBuilder[String]
    val extra = List.newBuilder[String]
    args.foreach {
      case x: Rule => rules += x.rule
      case x: Extra => extra += x.value
    }
    ShellArgs(rules.result(), extra.result())
  }
}

case class ShellArgs(
    rules: List[String],
    extra: List[String]
) {
  private def isExplicitFile(arg: String) =
    arg.startsWith("-f=") ||
      arg.startsWith("--files=")
  def explicitlyListsFiles: Boolean =
    extra.exists(isExplicitFile)
}

sealed abstract class ShellArg
case class Rule(rule: String) extends ShellArg
case class Extra(value: String) extends ShellArg
