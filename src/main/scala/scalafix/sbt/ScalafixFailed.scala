package scalafix.sbt

import sbt.FeedbackProvidedException
import scalafix.interfaces.ScalafixError

final class ScalafixFailed(val errors: List[ScalafixError])
    extends RuntimeException(errors.mkString(" "))
    with FeedbackProvidedException
