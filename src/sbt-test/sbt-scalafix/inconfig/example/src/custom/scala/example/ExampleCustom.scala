package example

object ExampleCustom {
  implicit val str = null.asInstanceOf[java.util.Map.Entry[Int, String]]
}
