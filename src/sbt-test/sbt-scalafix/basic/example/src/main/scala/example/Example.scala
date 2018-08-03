package example

object Example {
  implicit val str = null.asInstanceOf[java.util.Map.Entry[Int, String]]
}
