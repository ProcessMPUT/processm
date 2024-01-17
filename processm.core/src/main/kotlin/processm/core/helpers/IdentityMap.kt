package processm.core.helpers

import java.io.Serializable


private object IdentityMap : Serializable, Map<Any?, Any?> by emptyMap() {
    override operator fun get(key: Any?): Any? = key
}

/**
 * Returns an identity map that behaves like [emptyMap] but its [get] method returns the passed keys.
 */
fun <K> identityMap(): Map<K, K> = @Suppress("UNCHECKED_CAST") (IdentityMap as Map<K, K>)
