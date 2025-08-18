package scalafix.internal.sbt

import sbt.librarymanagement.*

import scalafix.sbt.InvalidArgument

trait Implicits {
  implicit class XtensionModuleID(m: ModuleID) {
    def asCoursierCoordinates: String = {
      m.crossVersion match {
        case _: Disabled =>
          s"${m.organization}:${m.name}:${m.revision}"
        case _: CrossVersion.Binary =>
          s"${m.organization}::${m.name}:${m.revision}"
        case _: CrossVersion.Full =>
          s"${m.organization}:::${m.name}:${m.revision}"
        case other =>
          throw new InvalidArgument(
            s"Unsupported crossVersion $other for dependency $m"
          )
      }
    }
  }
}

object Implicits extends Implicits
