scalaVersion := "2.12.18"

TaskKey[Unit]("check") := {
  val a = IO.read((Compile / sourceDirectory).value / "scala" / "A.scala")
  assert(a.contains("// v1 SemanticRule!"), a)

  val b = IO.read((Test / sourceDirectory).value / "scala" / "B.scala")
  assert(b.contains("// v1 SemanticRule!"), b)
}
