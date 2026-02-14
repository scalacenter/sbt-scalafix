package example

import java.util.Map
import scala.concurrent.Future

object Example {
  implicit val str = null.asInstanceOf[Map.Entry[Int, String]]
}
