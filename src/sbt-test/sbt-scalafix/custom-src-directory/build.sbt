scalaVersion := "2.13.17"

val app = project.settings(
  Compile / scalaSource := baseDirectory.value
)
