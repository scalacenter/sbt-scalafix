#!/usr/bin/env bash
set -eux

version=$1

cs resolve \
    --sbt-version 1.0 \
    --sbt-plugin "ch.epfl.scala:sbt-scalafix:$version"

cs resolve \
    --sbt-version 2.0.0-SNAPSHOT \
    --sbt-plugin "ch.epfl.scala:sbt-scalafix:$version"
