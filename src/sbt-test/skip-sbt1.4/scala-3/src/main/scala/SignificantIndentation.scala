object SignificantIndentation:
  implicit class XtensionVal(val str: String) extends AnyVal:
    def doubled: String = str + str
