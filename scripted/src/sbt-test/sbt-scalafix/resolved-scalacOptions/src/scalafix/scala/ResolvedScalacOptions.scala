import metaconfig.Configured
import scalafix.v1._

class ResolvedScalacOptions extends SyntacticRule("ResolvedScalacOptions") {
  override def withConfiguration(config: Configuration): Configured[Rule] =
    config.scalacOptions.filter(_.contains("${")) match {
      case Nil => Configured.ok(this)
      case unresolved =>
        Configured.error(
          s"unresolved scalacOptions: ${unresolved.mkString(" ")}"
        )
    }
}
