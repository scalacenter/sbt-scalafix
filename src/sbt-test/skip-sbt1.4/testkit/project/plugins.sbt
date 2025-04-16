resolvers += Resolver.sonatypeRepo("public")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % sys.props("plugin.version"))
libraryDependencies += "ch.epfl.scala" % "scalafix-interfaces" %
  sys.props("scalafix-interfaces.version")

libraryDependencies ++= {
  val sbtV = sbtBinaryVersion.value
  val scalaV = scalaBinaryVersion.value
  if (sbtVersion.value.startsWith("1."))
    Seq(
      Defaults.sbtPluginExtra(
        "com.eed3si9n" % "sbt-projectmatrix" % "0.8.0",
        sbtV,
        scalaV
      )
    )
  else
    Seq()
}
