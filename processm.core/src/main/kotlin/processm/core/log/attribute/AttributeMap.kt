package processm.core.log.attribute

/**
 * Attention! [get] throws if the key is not in the map instead of returning `null`.
 */
interface AttributeMap : Map<CharSequence, Any?> {

    companion object {
        //this is not a valid unicode character hence it should not occur in an attribute name. Neither is '\ufffe'
        private const val SEPARATOR_CHAR = '\uffff'
        const val SEPARATOR = SEPARATOR_CHAR.toString()
        const val EMPTY_KEY = (SEPARATOR_CHAR - 1).toString()

        init {
            assert(EMPTY_KEY == "\ufffe")
        }
    }

    val flat: Map<out CharSequence, Any?>

    fun getOrNull(key: CharSequence?): Any?

    val childrenKeys: Set<CharSequence>
    fun children(key: CharSequence): AttributeMap
}