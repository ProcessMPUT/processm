package processm.core.log.attribute


class Tag internal constructor()

/**
 * Attention! [get] throws if the key is not in the map instead of returning `null`.
 */
interface AttributeMap : Map<String, Any?> {

    companion object {
        /**
         * Unit separator https://en.wikipedia.org/wiki/C0_and_C1_control_codes#Field_separators
         */
        const val SEPARATOR_CHAR = '\u001f'
        const val SEPARATOR = SEPARATOR_CHAR.toString()
        const val AFTER_SEPARATOR_CHAR = (SEPARATOR_CHAR + 1).toString()

        const val INT_MARKER = 'i'
        const val STRING_MARKER = 's'
        val LIST_TAG = Tag()
    }

    val flat: Map<String, Any?>

    fun getOrNull(key: String?): Any?

    val childrenKeys: Set<Any>
    fun children(key: String): AttributeMap
    fun children(key: Int): AttributeMap

    fun asList(): List<AttributeMap> = AttributeMapAsList(this)
}

private class AttributeMapAsList(val base: AttributeMap) : AbstractList<AttributeMap>() {
    override val size: Int = base.childrenKeys.filterIsInstance<Int>().maxOfOrNull { it + 1 } ?: 0

    override fun get(index: Int): AttributeMap = base.children(index)

    override fun isEmpty(): Boolean = size == 0
}