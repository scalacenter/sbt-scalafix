package scalafix.internal.sbt

import sbt.ModuleID
import scala.util.matching.Regex

case class DependencyRule(
    ruleName: String,
    dependency: ModuleID
)

object DependencyRule {
  lazy val Parsed: Regex =
    "dependency:(.+)@([^:]+)::?([^:]+):([^:]+)".r
  def format: String =
    "dependency:$RULE_NAME@$ORGANIZATION::$ARTIFACT_NAME:$VERSION"
}
