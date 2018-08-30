package scalafix.internal.sbt

import sbt.util.Logger
import scalafix.interfaces.ScalafixDiagnostic
import scalafix.interfaces.ScalafixLintID
import scalafix.interfaces.ScalafixMainCallback
import scalafix.interfaces.ScalafixSeverity

class ScalafixLogger(logger: Logger) extends ScalafixMainCallback {
  def fullStringID(lintID: ScalafixLintID): String =
    if (lintID.categoryID().isEmpty) lintID.ruleName()
    else if (lintID.ruleName().isEmpty) lintID.categoryID()
    else s"${lintID.ruleName()}.${lintID.categoryID()}"
  override def reportDiagnostic(diagnostic: ScalafixDiagnostic): Unit = {
    def formatMessage: String = {
      val prefix =
        if (diagnostic.lintID().isPresent) {
          val id = fullStringID(diagnostic.lintID().get)
          s"[$id] "
        } else {
          ""
        }
      val message = prefix + diagnostic.message()

      if (diagnostic.position().isPresent) {
        val severity =
          diagnostic.severity().toString.toLowerCase()
        diagnostic.position().get().formatMessage(severity, message)
      } else {
        message
      }
    }
    diagnostic.severity() match {
      case ScalafixSeverity.INFO => logger.info(formatMessage)
      case ScalafixSeverity.WARNING => logger.warn(formatMessage)
      case ScalafixSeverity.ERROR => logger.error(formatMessage)
    }
  }
}
