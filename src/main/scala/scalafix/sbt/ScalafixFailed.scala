package scalafix.sbt

import sbt.FeedbackProvidedException
import scalafix.interfaces.ScalafixError

final class MissingSemanticdb(names: String)
    extends RuntimeException(
      s"""|The semanticdb-scalac compiler plugin is required to run semantic rules like $names.
          |To fix this problem for this sbt shell session, run `scalafixEnable` and try again.
          |To fix this problem permanently for your build, add the following settings to build.sbt:
          |  addCompilerPlugin(scalafixSemanticdb)
          |  scalacOptions += "-Yrangepos"
          |""".stripMargin
    )
    with FeedbackProvidedException
final class InvalidArgument(msg: String)
    extends RuntimeException(msg)
    with FeedbackProvidedException
final class ScalafixFailed(val errors: List[ScalafixError])
    extends RuntimeException(errors.mkString(" "))
    with FeedbackProvidedException
