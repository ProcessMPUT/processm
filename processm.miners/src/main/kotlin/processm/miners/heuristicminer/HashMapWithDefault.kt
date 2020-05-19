package processm.miners.heuristicminer

/**
 * Map with [get] behaving like [getOrPut].
 *
 * Cannot call [getOrPut] directly, as it is an extension function which calls [get]
 */
class HashMapWithDefault<K, V>(private val default: () -> V) : HashMap<K, V>() {
    override operator fun get(key: K): V {
        val result = super.get(key)
        return if (result == null) {
            val new = default()
            this[key] = new
            new
        } else
            result
    }
}