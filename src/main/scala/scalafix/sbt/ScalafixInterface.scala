package scalafix.sbt

import com.geirsson.coursiersmall._
import java.net.URLClassLoader
import scala.language.reflectiveCalls

trait ScalafixInterface {
  def main(args: Array[String]): Unit
}

object ScalafixInterface {

  def classloadInstance(): ScalafixInterface = {
    val dep = new Dependency(
      "ch.epfl.scala",
      s"scalafix-cli_${BuildInfo.scala212}",
      BuildInfo.scalafix
    )
    val settings = new Settings()
      .withDependencies(List(dep))
      .withRepositories(
        List(
          Repository.MavenCentral,
          Repository.SonatypeSnapshots,
          Repository.Ivy2Local
        )
      )
    val jars = CoursierSmall.fetch(settings)
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
