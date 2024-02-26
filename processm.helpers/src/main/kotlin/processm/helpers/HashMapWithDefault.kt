package processm.helpers

/**
 * [HashMap] in which [get] returns the value produced using the [default] initializer for absent keys.
 * @param storeDefault controls whether to store in the map the value produced using [default] (like [computeIfAbsent])
 * or just return and forget the produced value (like `get(key) ?: default(key)`).
 */
open class HashMapWithDefault<K, V>(
    private val storeDefault: Boolean = true,
    private val default: (key: K) -> V
) : HashMap<K, V>() {
    override operator fun get(key: K): V =
        if (storeDefault) super.computeIfAbsent(key) { default(it) }
        else super.get(key) ?: default(key)
}
