# Agent guide

## Project layout

- `plugin/src/main/scala/` contains sources shared by both compiler lines.
- `plugin/src/main/scala-2.12/` and `plugin/src/main/scala-3/` contain compiler-specific compatibility code. Keep behavior aligned when changing either implementation.
- `plugin/src/test/scala/` contains the fast ScalaTest unit tests.
- `scripted/src/sbt-test/<group>/<test>/` contains isolated sbt integration-test builds. Most tests have a `build.sbt` and a `test` script; edit the fixture and its assertions together.
- `build.sbt`, `project/Dependencies.scala`, `project/ScriptedAxis.scala`, and `project/plugins.sbt` define the build and its cross-build/test matrix. `project/plugins.sbt` also adds plugin sources to the meta-build for build-time dogfooding.

## Build matrix

- The plugin is built for Scala 2.12.x and Scala 3.8.x. Scala 2.12 targets Java 8 bytecode; Scala 3 targets Java 17 bytecode. Both configurations use strict compiler warnings as errors.
- `pluginCrossBuild / sbtVersion` sets the minimum sbt version for each plugin line. The scripted runner matrix is separate: it currently tests sbt 1.x with Scala 2.12, and sbt 2.0.x plus 2.1.x with Scala 3. Keep these compatibility floors and test versions distinct when changing the build.
- `ScriptedAxis` derives a matrix axis from the launched sbt version (sbt 1.x uses suffix `1`; sbt 2.x uses its first two version components). Preserve this mapping when adjusting scripted axes.
- The build enables SemanticDB and runs the example Scalafix rule against the repository. Shared library dependency versions are maintained in `project/Dependencies.scala`.
- Scripted setup reads Scalafix interface snapshot metadata from Sonatype. Scripted tests therefore may need network access and should not assume a fixed snapshot version.
- Scripted execution enables batching and allows two instances in parallel. Keep fixtures independent of execution order and avoid shared mutable state between tests.
- The scripted launch options set `sbt-scalafix.uselastmodified=true` because the caching fixture checks file-attribute-based invalidation. Do not remove or change this option without updating that fixture; its assertions intentionally depend on file timestamps, not only file contents.

## Verification

Run checks relevant to the change before submitting it:

```bash
# Fast unit tests; use scripted for the slower end-to-end integration tests.
sbt test
sbt scripted

# Run one scripted test (use sbt tab completion for $NAME).
sbt "scripted sbt-scalafix/$NAME"

# Formatting and lint checks (also enforced in CI).
./bin/scalafmt --test
sbt "scalafixAll --check"
```

To format code, run `./bin/scalafmt`; `./bin/scalafmt --diff` limits formatting to files changed from the main branch.

## CI

- `.github/workflows/ci.yml` runs tests on Ubuntu across the current Java LTS releases and the latest Java release when it is not LTS, for Scala 2.12 and Scala 3. Windows coverage is for Scala 3 on an LTS Java release.
- Each test job runs both unit tests and scripted tests for its Scala binary version. The focused commands are:

```bash
sbt "...@scalaBinaryVersion=2.12/test"
sbt "...@scalaBinaryVersion=2.12/scripted"
sbt "...@scalaBinaryVersion=3/test"
sbt "...@scalaBinaryVersion=3/scripted"
```

- Before testing, CI conditionally removes `scripted/src/sbt-test/skip-sbt1.4` and `scripted/src/sbt-test/skip-windows` based on the Java runtime and platform. Keep these fixture paths and workflow conditions in sync when changing scripted-test compatibility coverage.
- The separate formatting job enforces `./bin/scalafmt --test` and `sbt "scalafixAll --check"`.
- Recheck this workflow when changing Scala/sbt axes, Java-runtime or OS coverage, or scripted-test groups; local aggregate commands do not replace the CI matrix.

## Change guidance

- Keep Scala 2.12 and Scala 3 behavior compatible; use the matching source-specific directory only where APIs or syntax require it.
- Treat scripted fixtures as executable tests of real consumer builds. Preserve their sbt/Scala compatibility assumptions and expected source transformations.
- When changing build plugins, dependency versions, cross-build axes, or compiler options, run the focused checks for both Scala lines and the scripted suite where practical.
- Follow `CONTRIBUTING.md` for IDE setup and contributor guidance. Do not run release publishing tasks as part of routine verification.
