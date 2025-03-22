#!/usr/bin/env bash
set -eux

version=$1

cs resolve \
    --sbt-version 1.0 \
    --sbt-plugin "ch.epfl.scala:sbt-scalafix:$version"

cs resolve "ch.epfl.scala:sbt-scalafix_sbt2.0.0-M4_3:$version"
