package sbt.internal.sbtscalafix

import sbt.FileInfo
import scalafix.internal.sbt.Arg
import sjsonnew._

import scala.util.DynamicVariable

object Caching {

  val lastModifiedStyle = FileInfo.lastModified

  trait CacheKeysStamper
      extends JsonFormat[Seq[Arg.CacheKey]]
      with BasicJsonProtocol {
    private val builder = new DynamicVariable[Builder[_]](null)

    protected def stamp: Arg.CacheKey => Unit

    final def write[T](t: T)(implicit format: JsonFormat[T]): Unit =
      format.write(t, builder.value)

    override final def write[J](obj: Seq[Arg.CacheKey], b: Builder[J]): Unit = {
      b.beginArray()
      builder.withValue(b) {
        obj.foreach(stamp)
      }
      b.endArray()
    }

    // we actually don't need to read anything back, see https://github.com/sbt/sbt/pull/5513
    override final def read[J](
        jsOpt: Option[J],
        unbuilder: Unbuilder[J]
    ): Seq[Arg.CacheKey] = ???
  }

}
