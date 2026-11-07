import sbt._

object Compat {
  // https://github.com/sbt/sbt/blob/78bffa2/main/src/main/scala/sbt/Keys.scala#L577 for older sbt versions
  val allowUnsafeScalaLibUpgrade = settingKey[Boolean]("")
}
