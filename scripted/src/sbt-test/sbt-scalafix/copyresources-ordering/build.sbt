// Regression test for scalacenter/scalafix#2469.
//
// The semantic scalafix task reads `classDirectory`, which scalameta walks to
// build the symbol table. `copyResources` writes into that same directory, so
// the explicit scalafix invocation must depend on `copyResources`, otherwise
// the walk can race resource copying and observe a file mid-rename.
//
// `copyResources` is made to write a marker file. Running only `Compile /
// scalafix` must trigger `copyResources` (via the dependency added by the fix),
// so the marker is present. Without the dependency the marker is absent and
// `checkMarker` fails.

inThisBuild(
  List(
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    scalaVersion := "2.13.18"
  )
)

scalacOptions += "-Wunused"

resolvers += MavenRepository(
  "sonatype-central-maven-snapshots",
  "https://central.sonatype.com/repository/maven-snapshots/"
)

lazy val marker =
  settingKey[File]("Marker file written by copyResources")

marker := (Compile / classDirectory).value / "copyResources-2469.marker"

Compile / copyResources := {
  val copied = (Compile / copyResources).value
  IO.write(marker.value, "copied")
  copied
}

lazy val checkMarker =
  taskKey[Unit]("Assert copyResources ran as part of scalafix")

import _root_.scalafix.internal.sbt.Compat._
checkMarker := Def.uncached {
  assert(
    marker.value.exists(),
    s"marker missing at ${marker.value}: copyResources did not run as part of scalafix (#2469)"
  )
}
