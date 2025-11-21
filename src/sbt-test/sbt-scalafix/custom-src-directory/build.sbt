scalaVersion := "2.13.18"

val app = project.settings(
  Compile / scalaSource := baseDirectory.value
)
