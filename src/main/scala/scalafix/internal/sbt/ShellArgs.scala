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
  private val filesArgsPrefixes = Seq("-f=", "--files=")

  lazy val explicitFiles: List[String] =
    extra.flatMap { e =>
      filesArgsPrefixes.collectFirst {
        case prefix if e.startsWith(prefix) =>
          e.stripPrefix(prefix)
      }
    }

}

sealed abstract class ShellArg
case class Rule(rule: String) extends ShellArg
case class Extra(value: String) extends ShellArg
