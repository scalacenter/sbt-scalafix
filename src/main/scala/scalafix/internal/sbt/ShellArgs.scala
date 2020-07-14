package scalafix.internal.sbt

object ShellArgs {

  sealed abstract class Arg
  case class Rule(value: String) extends Arg
  case class File(value: String) extends Arg
  case class Extra(key: String, value: Option[String] = None) extends Arg
  case object NoCache extends Arg

  def apply(args: Seq[Arg]): ShellArgs = {
    val rules = List.newBuilder[String]
    val files = List.newBuilder[String]
    val extra = List.newBuilder[String]
    var noCache = false
    args.foreach {
      case x: Rule => rules += x.value
      case x: File => files += x.value
      case x: Extra => extra += x.value.foldLeft(x.key)((k, v) => s"$k=$v")
      case NoCache => noCache = true
    }
    ShellArgs(rules.result(), files.result(), extra.result(), noCache)
  }
}

case class ShellArgs(
    rules: List[String] = Nil,
    files: List[String] = Nil,
    extra: List[String] = Nil,
    noCache: Boolean = false
) {
  def +(that: ShellArgs): ShellArgs =
    ShellArgs(
      this.rules ::: that.rules,
      this.files ::: that.files,
      this.extra ::: that.extra,
      this.noCache || that.noCache
    )
}
