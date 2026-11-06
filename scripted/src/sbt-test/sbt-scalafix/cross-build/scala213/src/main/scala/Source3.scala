open class Source3

object Source3 {
  implicit class XtensionVal(val str: String) extends AnyVal {
    def doubled: String = str + str
  }
}
