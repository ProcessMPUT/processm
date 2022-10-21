package processm.core.log

/**
 * Attention! [get] throws if the key is not in the map instead of returning `null`.
 */
interface AttributeMap : Map<String, Any?> {

    companion object {
        //TODO revisit values, possibly ensure that keys supplied by the user don't use character above these two
        const val EMPTY_KEY = "\uc07f"
        const val SEPARATOR = "\uc080"
    }

    val flat: Map<String, Any?>

    fun getOrNull(key: String?): Any?

    operator fun get(key: List<String>): Any?

    val childrenKeys: Set<String>
    fun children(key: String): AttributeMap

    fun children(key: List<String>): AttributeMap
}