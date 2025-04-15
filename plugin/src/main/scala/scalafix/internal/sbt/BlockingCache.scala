package scalafix.internal.sbt

import java.lang.ref.SoftReference
import java.util as ju

/** A basic thread-safe cache with arbitrary eviction on GC pressure. */
class BlockingCache[K, V] {

  private val underlying =
    new ju.concurrent.ConcurrentHashMap[K, SoftReference[V]]

  // Evaluations of `transform` are guaranteed to be sequential for the same key
  def compute(key: K, transform: Option[V] => V): V = {
    // keep the result in a strong reference to avoid eviction
    // just after executing wrapped compute()
    var result: V = null.asInstanceOf[V]
    underlying.compute(
      key,
      (_, prev: SoftReference[V]) => {
        result = transform(Option(prev).flatMap(ref => Option(ref.get)))
        new SoftReference(result)
      }
    )
    result
  }
}
