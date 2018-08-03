package scalafix.sbt

import java.net.URLClassLoader
import java.nio.file.Path
import scala.language.reflectiveCalls

trait ScalafixInterface {
  def main(args: Array[String]): Unit
}

object ScalafixInterface {

  def classloadInstance(jars: List[Path]): ScalafixInterface = {
    type Main = {
      def main(args: Array[String]): Unit
    }
    val urls = jars.iterator.map(_.toUri.toURL).toArray
    val classloader = new URLClassLoader(urls, null)
    val cls = classloader.loadClass("scalafix.v1.Main$")
    val ctor = cls.getDeclaredConstructor()
    ctor.setAccessible(true)
    val cli = ctor.newInstance().asInstanceOf[Main]
    new ScalafixInterface {
      override def main(args: Array[String]): Unit = {
        cli.main(args)
      }
    }
  }
}
