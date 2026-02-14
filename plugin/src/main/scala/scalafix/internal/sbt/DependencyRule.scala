package scalafix.internal.sbt

import scala.util.matching.Regex

import sbt.ModuleID

case class DependencyRule(
    ruleName: String,
    dependency: ModuleID
)

object DependencyRule {
  lazy val Binary: Regex =
    "dependency:(.+)@([^:]+)::?([^:]+):([^:]+)".r
  lazy val Full: Regex =
    "dependency:(.+)@([^:]+):::([^:]+):([^:]+)".r
  def format: String =
    "dependency:$RULE_NAME@$ORGANIZATION::$ARTIFACT_NAME:$VERSION"
}
