package processm.helpers

/**
 * A [LinkedHashMap] such that the order is the update order, not the insertion order.
 * This implementation is incomplete, as the only implemented way of updating an entry is by using [put].
 *
 * MRU stands for most-recently updated
 */
class MRUMap<K, V> : LinkedHashMap<K, V>() {
    override fun put(key: K, value: V): V? {
        remove(key)
        return super.put(key, value)
    }
}
