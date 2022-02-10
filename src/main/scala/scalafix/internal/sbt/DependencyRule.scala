package scalafix.internal.sbt

import sbt.ModuleID
import scala.util.matching.Regex

case class DependencyRule(
    ruleName: String,
    dependency: ModuleID
)

object DependencyRule {
  lazy val Deprecated: Regex =
    "dependency:(.+)@(.+):(.+):(.+)".r
  def deprecatedFormat: String =
    "dependency:$RULE_NAME@$ORGANIZATION:$ARTIFACT_NAME:$VERSION"

  lazy val Parsed: Regex =
    "dependency:(.+)@(.+)::(.+):(.+)".r
  def format: String =
    "dependency:$RULE_NAME@$ORGANIZATION::$ARTIFACT_NAME:$VERSION"
}
