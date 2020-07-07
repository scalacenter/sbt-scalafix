package scalafix.internal.sbt

import scala.collection.mutable

/** A basic thread-safe cache without any eviction. */
class BlockingCache[K, V] {
  private val underlying = new mutable.HashMap[K, V]

  /**
    * @param value By-name parameter evaluated when the key if missing. Value computation is guaranteed
    *              to be called only once per key across all invocations.
    */
  def getOrElseUpdate(key: K, value: => V): V = {
    // ConcurrentHashMap does not guarantee that there is only one evaluation of the value, so
    // we use our own (global) locking, which is OK as the number of keys is expected to be
    // very small (bound by the number of projects in the sbt build).
    underlying.synchronized {
      underlying.getOrElseUpdate(key, value)
    }
  }
}
