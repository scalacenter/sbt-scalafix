scalaVersion := "2.12.7"

TaskKey[Unit]("check") := {
  val text = IO.read((Compile / sourceDirectory).value / "scala" / "A.scala")
  assert(text.contains("// v1 SemanticRule!"), text)
}
