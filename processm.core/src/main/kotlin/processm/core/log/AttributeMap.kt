package processm.core.log

/**
 * Attention! [get] throws if the key is not in the map instead of returning `null`.
 */
interface AttributeMap : Map<String, Any?> {
    fun getOrNull(key: String?): Any?

    operator fun get(key: List<String>): Any?

    val childrenKeys: Set<String>
    fun children(key: String): AttributeMap

    fun children(key: List<String>): AttributeMap
}