import _root_.scalafix.sbt.{BuildInfo => Versions}

scalaVersion := Versions.scala213
// on sbt 2.x, adds -Xplugin:${CSR_CACHE}/... (and -P:semanticdb:targetroot:${OUT}/... since 2.1)
semanticdbEnabled := true
semanticdbVersion := scalafixSemanticdb.revision

libraryDependencies +=
  "ch.epfl.scala" %% "scalafix-core" % Versions.scalafixVersion % ScalafixConfig
