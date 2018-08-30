package scalafix.internal.sbt

object ShellArgs {
  def apply(args: Seq[ShellArg]): ShellArgs = {
    val rules = List.newBuilder[Rule]
    val extra = List.newBuilder[String]
    args.foreach {
      case x: Rule => rules += x
      case x: Extra => extra += x.value
    }
    ShellArgs(rules.result(), extra.result())
  }
}

case class ShellArgs(
    rules: List[Rule],
    extra: List[String]
) {
  private def isExplicitFile(arg: String) =
    arg.startsWith("-f=") ||
      arg.startsWith("--files=")
  def explicitlyListsFiles: Boolean =
    extra.exists(isExplicitFile)
  def asStrings: List[String] =
    rules.flatMap(r => "-r" :: r.name :: Nil) ++ extra
  def isMaybeSemantic: Boolean =
    rules.isEmpty || rules.exists(_.isMaybeSemantic)
}

sealed abstract class ShellArg
case class Rule(name: String, isMaybeSemantic: Boolean) extends ShellArg
case class Extra(value: String) extends ShellArg
