package processm.experimental.heuristicminer

/**
 * Map with [get] behaving like [getOrPut].
 *
 * Cannot call [getOrPut] directly, as it is an extension function which calls [get]
 */
class HashMapWithDefault<K, V>(private val default: () -> V) : HashMap<K, V>() {
    override operator fun get(key: K): V = super.computeIfAbsent(key) { default() }
}