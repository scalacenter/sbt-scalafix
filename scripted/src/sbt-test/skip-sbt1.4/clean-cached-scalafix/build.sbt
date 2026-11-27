import _root_.scalafix.sbt.BuildInfo

scalaVersion := BuildInfo.scala212

// Keep this test focused on the sbt 2.0 cache behavior before clean began
// invalidating cached tasks in sbt 2.1.
Compile / unmanagedSources / inputFileStamper := sbt.nio.FileStamper.LastModified
