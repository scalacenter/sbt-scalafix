# sbt-scalafix

This is the sbt plugin for [Scalafix](https://scalacenter.github.io/scalafix/),
a refactoring and linting tool for Scala. For instructions on how to install the
plugin, refer to the
[website](https://scalacenter.github.io/scalafix/docs/users/installation).

## Issues

Please report issues to the main scalafix repository:
https://github.com/scalacenter/scalafix/issues

## Nightlies

Our CI publishes a snapshot release to Sonatype on every merge into main. The latest snapshot(s) of
[the plugin](https://oss.sonatype.org/content/repositories/snapshots/ch/epfl/scala/sbt-scalafix_2.12_1.0/)
and/or [scalafix-interfaces](https://oss.sonatype.org/content/repositories/snapshots/ch/epfl/scala/scalafix-interfaces/)
can be used via:

```diff
  // project/plugins.sbt
+resolvers ++= Resolver.sonatypeOssRepos("snapshots")
-addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.14.2")
+addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.14.2+17-4ba873d2-SNAPSHOT")
-libraryDependencies += "ch.epfl.scala" % "scalafix-interfaces" % "0.14.2"
+libraryDependencies += "ch.epfl.scala" % "scalafix-interfaces" % "0.14.2+48-9b6e03ac-SNAPSHOT"
 ```

## Team

The current maintainers (people who can merge pull requests) are:

- Brice Jaglin - [`@bjaglin`](https://github.com/bjaglin)
- Ólafur Páll Geirsson - [`@olafurpg`](https://github.com/olafurpg)
