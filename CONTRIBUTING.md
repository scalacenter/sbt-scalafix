Contributing
===========

## Modules

- `plugin/` the sbt plugin

## Setting up an IDE

The project should import into IntelliJ like normal sbt builds.

## Testing

- `plugin/test`: to run fast unit tests
- `plugin/scripted`: to run slow integration tests
- `plugin/scripted sbt-scalafix/$NAME`: to run an individual integration test.
  Use tab completions to expan `$NAME`.

## Formatting

Be sure to run the `bin/scalafmt` script to ensure code formatting is OK.  Pro
tip, `./bin/scalafmt --diff` formats only the files that have changed from the
master branch.  You can read more about it at http://scalafmt.org

## TL;DR

If you are unsure about anything, don't hesitate to ask in the [gitter channel](https://gitter.im/scalacenter/scalafix).
