package processm.core.log.attribute


class Tag internal constructor()

/**
 * A tree of maps (i.e., a hierarchy of maps). The maps being direct children of this map are accessible by calling
 * [children] or [asList]. To values stored by this map are accessible using the exposed [Map] interface. All descendant
 * key-value pairs are accessible through [flatView], where the keys are constructed using the following grammar:
 *
 * ```
 * KEY := CHILD_KEY* VALUE_KEY
 * VALUE_KEY := SAFE_STRING
 * CHILD_KEY = SEPARATOR (STRING_KEY | INT_KEY) SEPARATOR
 * STRING_KEY := STRING_MARKER SAFE_STRING
 * INT_KEY := INT_MARKER INT
 * ```
 *
 * Where:
 *
 * * `INT` is a 10-based textual representation of an [Int]
 * * `SAFE_STRING` is a [String] not containing [SEPARATOR_CHAR]
 * * `SEPARATOR` denotes [SEPARATOR_CHAR]
 * * `STRING_MARKER` denotes [STRING_MARKER]
 * * `INT_MARKER` denotes [INT_MARKER]
 *
 * Such an elaborate grammar enables the following:
 * 1. Value keys and child keys cannot be confused.
 * 2. Child keys are always in the lexical range from [SEPARATOR_CHAR] to [AFTER_SEPARATOR_CHAR], thus children can be
 * easily found within the flat representation
 * 3. Integer keys and string keys cannot be confused, e.g., `"10"` and `10` remain distinct keys.
 * 4. Lists (i.e., key-less, ordered sequences of values) can be represented by using integer keys.
 *
 * Attention! [get] throws if the key is not in the map instead of returning `null`.
 */
interface AttributeMap : Map<String, Any?> {

    companion object {
        /**
         * A character used to separate two parts of a key. Since there is no escaping it cannot be used within a key.
         * Unit separator https://en.wikipedia.org/wiki/C0_and_C1_control_codes#Field_separators
         */
        const val SEPARATOR_CHAR = '\u001f'
        const val SEPARATOR = SEPARATOR_CHAR.toString()

        /**
         * A character right after [SEPARATOR_CHAR], denoting a starting point for a valid range of keys
         */
        const val AFTER_SEPARATOR_CHAR = SEPARATOR_CHAR + 1
        const val AFTER_SEPARATOR = AFTER_SEPARATOR_CHAR.toString()

        /**
         * A marker to distinguish integer child keys from string child keys
         */
        const val INT_MARKER = 'i'

        /**
         * A marker to distinguish string child keys from integer child keys
         */
        const val STRING_MARKER = 's'
        const val BEFORE_STRING = SEPARATOR + STRING_MARKER
        const val BEFORE_INT = SEPARATOR + INT_MARKER

        /**
         * An auxiliary object used in deserialization
         */
        val LIST_TAG = Tag()
    }

    /**
     * An unmodifiable view of the flat representation
     */
    val flatView: Map<String, Any?>

    /**
     * Returns the value associated with [key] if `key != null` and there is a value associated with it, and `null` otherwise
     */
    fun getOrNull(key: String?): Any?

    /**
     * The set of values that can be passed as an argument of [children] to obtain an [AttributeMap] which is non-empty and/or has non-empty [childrenKeys]
     */
    val childrenKeys: Set<Any>

    /**
     * The sub-map of this map corresponding to [key]
     */
    fun children(key: String): AttributeMap

    /**
     * The sub-map of this map corresponding to [key]. Integer keys are intended to represent lists.
     */
    fun children(key: Int): AttributeMap

    /**
     * A read-only view consisting of only children with integer keys. The integer keys can be arbitrary,
     * but [List.size] will be computed correctly only if they form a consecutive sequence from 0 to some `n`
     */
    fun asList(): List<AttributeMap> = AttributeMapAsList(this)
}

private class AttributeMapAsList(val base: AttributeMap) : AbstractList<AttributeMap>() {
    override val size: Int = base.childrenKeys.filterIsInstance<Int>().maxOfOrNull { it + 1 } ?: 0

    override fun get(index: Int): AttributeMap = base.children(index)

    override fun isEmpty(): Boolean = size == 0
}