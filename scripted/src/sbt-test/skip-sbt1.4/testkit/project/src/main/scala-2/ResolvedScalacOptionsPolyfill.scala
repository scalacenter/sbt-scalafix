import sbt._

/** sbt 1.x does not virtualize scalacOptions, so there is nothing to resolve */
object ResolvedScalacOptionsPolyfill extends AutoPlugin {
  object autoImport {
    val resolvedScalacOptions: TaskKey[Seq[String]] = Keys.scalacOptions
  }
}
