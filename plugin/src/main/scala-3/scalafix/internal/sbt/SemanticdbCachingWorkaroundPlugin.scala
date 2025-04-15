import sbt.*
import sbt.Keys.*
import sbt.plugins.JvmPlugin
import sbt.util.CacheImplicits.given

// https://github.com/sbt/sbt/pull/8086
object SemanticdbCachingWorkaroundPlugin extends AutoPlugin {

  override def trigger: PluginTrigger = allRequirements
  override def requires: Plugins = JvmPlugin

  override lazy val projectSettings: Seq[Def.Setting[?]] =
    Seq(Compile, Test).flatMap(c =>
      inConfig(c)(
        compileIncremental := Def.taskIf {
          if (semanticdbIncludeInJar.value || !semanticdbEnabled.value)
            compileIncremental.value
          else compileIncAndCacheSemanticdbTargetRootTask.value
        }.value
      )
    )

  private val compileIncAndCacheSemanticdbTargetRootTask = Def.cachedTask {
    val prev = compileIncremental.value
    val converter = fileConverter.value
    val targetRoot = semanticdbTargetRoot.value

    val vfTargetRoot = converter.toVirtualFile(targetRoot.toPath)
    Def.declareOutputDirectory(vfTargetRoot)
    prev
  }
}
