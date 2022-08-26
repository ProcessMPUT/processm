package processm.core.helpers

/**
 * A [LinkedHashMap] such that the order is the update order, not the insertion order.
 * This implementation is incomplete, as the only implemented way of updating an entry is by using [put].
 */
class LRUMap<K, V> : LinkedHashMap<K, V>() {
    override fun put(key: K, value: V): V? {
        remove(key)
        return super.put(key, value)
    }
}