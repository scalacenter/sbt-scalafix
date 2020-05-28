#!/usr/bin/env bash
set -eux

version=$1

cs resolve \
    "ch.epfl.scala:sbt-scalafix;sbtVersion=1.0;scalaVersion=2.12:$version" \
    --sbt-plugin-hack -r sonatype:public
cs resolve \
    "ch.epfl.scala:sbt-scalafix;sbtVersion=0.13;scalaVersion=2.10:$version" \
    --sbt-plugin-hack -r sonatype:public
