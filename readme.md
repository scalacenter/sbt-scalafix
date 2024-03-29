# sbt-scalafix

This is the sbt plugin for [Scalafix](https://scalacenter.github.io/scalafix/),
a refactoring and linting tool for Scala. For instructions on how to install the
plugin, refer to the
[website](https://scalacenter.github.io/scalafix/docs/users/installation).

## Issues

Please report issues to the main scalafix repository:
https://github.com/scalacenter/scalafix/issues

## Nightlies

Our CI publishes a [snapshot release to Sonatype](https://oss.sonatype.org/content/repositories/snapshots/ch/epfl/scala/sbt-scalafix_2.12_1.0/)
on every merge into main. The latest snapshot at the time of the writing can be used via:

```diff
  // project/plugins.sbt
-addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.34")
+addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.34+5-5dfe5fb6-SNAPSHOT")
+resolvers += Resolver.sonatypeRepo("snapshots")
 ```

## Team

The current maintainers (people who can merge pull requests) are:

- Brice Jaglin - [`@bjaglin`](https://github.com/bjaglin)
- Ólafur Páll Geirsson - [`@olafurpg`](https://github.com/olafurpg)
