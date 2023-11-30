import _root_.scalafix.sbt.{BuildInfo => Versions}

val scala3Version = "3.3.0"
semanticdbEnabled := true
semanticdbVersion := scalafixSemanticdb.revision

lazy val root = project
  .in(file("."))
  .settings(
    scalaVersion := Versions.scala212,
    crossScalaVersions := Seq(Versions.scala212, Versions.scala213, scala3Version),
    scalacOptions ++= (if (scalaVersion.value.startsWith("2")) Seq("-Ywarn-unused") else Seq()),
    (Compile / scalafix) := ((Compile / scalafix) dependsOn scalafixGenerateDynamicConfig).evaluated,
    scalafixScalaBinaryVersion := {
      if ((scalaBinaryVersion).value == "3") {
        (scalafixScalaBinaryVersion).value
      } else { (scalaBinaryVersion).value }
    }
  )

val scalafixGenerateDynamicConfig: TaskKey[Unit] =
  taskKey[Unit]("Generate .scalafix.conf file.")

scalafixGenerateDynamicConfig := {
  val log = sLog.value
  IO.write(file(".scalafix.conf"), if(scalaVersion.value.startsWith("3")) {
    log.success("Generating Scalafix for 3")
    """rules = [DisableSyntax, OrganizeImports]
       OrganizeImports.removeUnused = false
    """
  }
  else {
    log.success("Generating Scalafix for 2")
    """rules = [DisableSyntax, OrganizeImports, ExplicitResultTypes]
       OrganizeImports.removeUnused = true
       ExplicitResultTypes.skipSimpleDefinitions = false
    """
  })
}
