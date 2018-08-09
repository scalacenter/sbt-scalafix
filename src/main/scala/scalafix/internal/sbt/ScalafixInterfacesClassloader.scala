package scalafix.internal.sbt

/**
  * A classloader that shares only scalafix-interfaces classes from the parent classloader.
  *
  * This classloader is intended to be used as a parent when class-loading scalafix-cli.
  * By using this classloader as a parent, it's possible to cast runtime instances from
  * the scalafix-cli classloader into `scalafix.interfaces.Scalafix` from this classlaoder.
  */
class ScalafixInterfacesClassloader(parent: ClassLoader)
    extends ClassLoader(null) {
  override def findClass(name: String): Class[_] = {
    if (name.startsWith("scalafix.interfaces")) {
      parent.loadClass(name)
    } else {
      throw new ClassNotFoundException(name)
    }
  }
}
