scalaVersion := "2.13.16"

val app = project.settings(
  Compile / scalaSource := baseDirectory.value
)
