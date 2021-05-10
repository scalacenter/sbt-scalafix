package scalafix.internal.sbt

import sbt._

import scala.util.Try

/** Helper to use sbt 1.3+ SemanticdbPlugin features when available */
object SemanticdbPlugin {

  // Copied from https://github.com/sbt/sbt/blob/v1.3.0/main/src/main/scala/sbt/Keys.scala#L190-L195
  val semanticdbEnabled = settingKey[Boolean]("")
  val semanticdbVersion = settingKey[String]("")

  lazy val available = Try {
    Class.forName("sbt.plugins.SemanticdbPlugin")
    true
  }.getOrElse(false)

}
