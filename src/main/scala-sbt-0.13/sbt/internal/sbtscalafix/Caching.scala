package sbt.internal.sbtscalafix

import java.io.ByteArrayOutputStream

import sbinary._
import sbt._
import scalafix.internal.sbt.Arg

import scala.util.DynamicVariable

object Caching {

  val lastModifiedStyle = FilesInfo.lastModified
  val hashStyle = FilesInfo.hash

  trait CacheKeysStamper
      extends InputCache[Seq[Arg.CacheKey]]
      with CacheImplicits {
    private val output = new DynamicVariable[Output](null)

    implicit val lastModifiedFormat = FileInfo.lastModified.format
    val baFormat = implicitly[Format[Array[Byte]]]
    val baEquiv = implicitly[Equiv[Array[Byte]]]

    protected def stamp: Arg.CacheKey => Unit

    final def write[T](t: T)(implicit format: Format[T]): Unit =
      format.writes(output.value, t)

    override final type Internal = Array[Byte]
    override final def convert(keys: Seq[Arg.CacheKey]): Array[Byte] = {
      val baos = new ByteArrayOutputStream()
      output.withValue(new JavaOutput(baos)) {
        keys.foreach(stamp)
      }
      Hash.apply(baos.toByteArray)
    }
    override final def read(from: Input): Array[Byte] =
      baFormat.reads(from)
    override final def write(to: Output, ba: Array[Byte]): Unit =
      baFormat.writes(to, ba)
    override final def equiv: Equiv[Array[Byte]] =
      baEquiv
  }
}
