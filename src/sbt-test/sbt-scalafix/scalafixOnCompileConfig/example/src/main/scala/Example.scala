//can be removed by RemoveUnused
import java.time.Instant

object Example {
  //can be removed by RemoveUnused
  private val f: String = null

  //cannot be removed by RemoveUnused
  def g(): Unit = return
}
