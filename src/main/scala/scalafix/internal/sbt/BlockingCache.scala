package scalafix.internal.sbt

import java.{util => ju}

/** A basic thread-safe cache without any eviction. */
class BlockingCache[K, V] {

  // Number of keys is expected to be very small so the global lock should not be a bottleneck
  private val underlying = ju.Collections.synchronizedMap(new ju.HashMap[K, V])

  /** @param value By-name parameter evaluated when the key if missing. Value computation is guaranteed
    *              to be called only once per key across all invocations.
    */
  def getOrElseUpdate(key: K, value: => V): V =
    underlying.computeIfAbsent(
      key,
      new ju.function.Function[K, V] {
        override def apply(k: K): V = value
      }
    )
}
