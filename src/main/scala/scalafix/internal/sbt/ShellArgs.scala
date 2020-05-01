package scalafix.internal.sbt

object ShellArgs {

  sealed abstract class Arg
  case class Rule(value: String) extends Arg
  case class File(value: String) extends Arg
  case class Extra(key: String, value: Option[String] = None) extends Arg

  def apply(args: Seq[Arg]): ShellArgs = {
    val rules = List.newBuilder[String]
    val files = List.newBuilder[String]
    val extra = List.newBuilder[String]
    args.foreach {
      case x: Rule => rules += x.value
      case x: File => files += x.value
      case x: Extra => extra += x.value.foldLeft(x.key)((k, v) => s"$k=$v")
    }
    ShellArgs(rules.result(), files.result(), extra.result())
  }
}

case class ShellArgs(
    rules: List[String] = Nil,
    files: List[String] = Nil,
    extra: List[String] = Nil
)
