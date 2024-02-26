package processm.helpers

/**
 * A map with counting capabilities
 */
class Counter<K> : HashMap<K, Int>() {

    /**
     * Returns the stored value if the key is present and 0 otherwise
     */
    override operator fun get(key: K): Int {
        return super.get(key) ?: 0
    }

    /**
     * Increments the value stored for [key] by [n]
     */
    fun inc(key: K, n: Int = 1) {
        compute(key) { _, v -> (v ?: 0) + n }
    }

    /**
     * Increments the value stored for [keys] by [n] each. Duplicated keys are treated separately.
     */
    fun inc(keys: Collection<K>, n: Int = 1) {
        for (it in keys)
            inc(it, n)
    }

    /**
     * Decrements the value stored for [key] by [n], capping at 0
     */
    fun dec(key: K, n: Int = 1) {
        compute(key) { _, v -> if (v != null && v > n) v - n else null }
    }
}
