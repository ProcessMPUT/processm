package processm.core.log.attribute

import processm.core.helpers.mapToSet
import processm.core.log.attribute.AttributeMap.Companion.AFTER_SEPARATOR
import processm.core.log.attribute.AttributeMap.Companion.BEFORE_INT
import processm.core.log.attribute.AttributeMap.Companion.BEFORE_STRING
import processm.core.log.attribute.AttributeMap.Companion.INT_MARKER
import processm.core.log.attribute.AttributeMap.Companion.SEPARATOR
import processm.core.log.attribute.AttributeMap.Companion.SEPARATOR_CHAR
import processm.core.log.attribute.AttributeMap.Companion.STRING_MARKER
import processm.core.log.isAllowedAttributeValue
import java.time.Instant
import java.util.*

/**
 * A mutable implementation of [AttributeMap]
 *
 * Attention! [get] throws if the key is not in the map instead of returning `null`.
 * Similarly, [computeIfAbsent] assigns `null` instead of ignoring it
 */
open class MutableAttributeMap(
    protected val flat: SortedMap<String, Any?> = TreeMap()
) : AttributeMap {

    companion object {
        @JvmStatic
        protected val comparator = Comparator<String> { a, b ->
            a.compareTo(b)
        }
    }

    protected open val stringPrefix: String
        get() = BEFORE_STRING

    protected open val intPrefix: String
        get() = BEFORE_INT

    /**
     * Copies [map]
     */
    constructor(map: AttributeMap) : this() {
        flat.putAll(map.flatView)
    }

    protected open fun childrenRange(key: String): Pair<String, String> {
        require(SEPARATOR_CHAR !in key)
        val prefix = stringPrefix + key
        return prefix + SEPARATOR to prefix + AFTER_SEPARATOR
    }

    protected open fun childrenRange(key: Int): Pair<String, String> {
        val prefix = intPrefix + key.toString()
        return prefix + SEPARATOR to prefix + AFTER_SEPARATOR
    }

    private inline fun unsafeSet(key: String, value: Any?) {
        require(SEPARATOR_CHAR !in key)
        flat[key] = value
    }

    /**
     * Associates [key] with [value]
     *
     * @throws IllegalArgumentException if [value] is not one of the allowed data types
     */
    open fun safeSet(key: String, value: Any?) {
        require(value.isAllowedAttributeValue())
        unsafeSet(key, value)
    }

    /**
     * Associates [key] with [value]. If [value] is `null` it is stored rather than deleting the key.
     */
    open operator fun set(key: String, value: String?) {
        unsafeSet(key, value)
    }

    /**
     * Associates [key] with [value].
     */
    open operator fun set(key: String, value: Long) {
        unsafeSet(key, value)
    }

    /**
     * Associates [key] with [value].
     */
    open operator fun set(key: String, value: Double) {
        unsafeSet(key, value)
    }

    /**
     * Associates [key] with [value].
     */
    open operator fun set(key: String, value: Instant) {
        unsafeSet(key, value)
    }

    /**
     * Associates [key] with [value].
     */
    open operator fun set(key: String, value: UUID) {
        unsafeSet(key, value)
    }

    /**
     * Associates [key] with [value].
     */
    open operator fun set(key: String, value: Boolean) {
        unsafeSet(key, value)
    }

    /**
     * Associates [key] with [value].
     */
    open operator fun set(key: String, value: Tag) {
        unsafeSet(key, value)
    }

    override operator fun get(key: String): Any? = flat.getValue(key)
    override val flatView: Map<String, Any?>
        get() = Collections.unmodifiableMap(flat)

    override fun getOrNull(key: String?): Any? = if (key !== null) flat[key] else null

    protected fun childrenKeys(prefix: String): Set<Any> {
        val prefixWithSeparatorLength = prefix.length + 1
        return flat.subMap(prefix + SEPARATOR, prefix + AFTER_SEPARATOR).keys.mapToSet {
            val end = it.indexOf(SEPARATOR_CHAR, prefixWithSeparatorLength)
            val s = it.substring(prefixWithSeparatorLength, end)
            if (s[0] == STRING_MARKER)
                s.substring(1, s.length)
            else {
                assert(s[0] == INT_MARKER)
                s.substring(1, s.length).toInt()
            }
        }
    }

    override val childrenKeys: Set<Any>
        get() = childrenKeys("")

    override fun children(key: String): MutableAttributeMap {
        val (s, e) = childrenRange(key)
        return MutableAttributeMapWithPrefix(flat.subMap(s, e), s)
    }

    override fun children(key: Int): MutableAttributeMap {
        val (s, e) = childrenRange(key)
        return MutableAttributeMapWithPrefix(flat.subMap(s, e), s)
    }

    protected open val top: MutableMap<String, Any?>
        get() = SplitMutableMap(flat.headMap(SEPARATOR), flat.tailMap(AFTER_SEPARATOR), SEPARATOR, comparator)

    override val entries: Set<Map.Entry<String, Any?>>
        get() = top.entries

    override val keys: Set<String>
        get() = top.keys

    override val size: Int
        get() = top.size

    override val values: Collection<Any?>
        get() = top.values

    // The following method reference flat to avoid creating top if flat is empty and thus top will be empty as well
    override fun isEmpty(): Boolean = flat.isEmpty() || top.isEmpty()
    override fun containsValue(value: Any?): Boolean = flat.isNotEmpty() && top.containsValue(value)

    override fun containsKey(key: String): Boolean = flat.isNotEmpty() && top.containsKey(key)

    /**
     * Associates [key] with the result of invoking [ctor] and returns this result, if [key] was not present in the map.
     * Otherwise, returns the value already present.
     */
    fun computeIfAbsent(key: String, ctor: (key: String) -> Any?): Any? =
        if (!containsKey(key)) {
            val value = ctor(key)
            safeSet(key, value)
            value
        } else
            get(key)

    override fun hashCode(): Int = flat.hashCode()

    override fun equals(other: Any?): Boolean {
        if (other is MutableAttributeMap) {
            return this.flat == other.flat
        }
        if (other is Map<*, *>) {
            return this.flat == other
        }
        return false
    }

    /**
     * Associates [key] with [value] assuming [key] is a key for the flat representation, i.e.,
     * follows the grammar described in the class documentation.
     *
     * This function is intended only for deserialization and should be otherwise avoided.
     */
    fun putFlat(key: String, value: Any) {
        require(value.isAllowedAttributeValue())
        flat[key] = value
    }
}

fun AttributeMap.toMutableAttributeMap() = MutableAttributeMap(this)

fun mutableAttributeMapOf(vararg pairs: Pair<String, Any?>): MutableAttributeMap =
    MutableAttributeMap().apply {
        pairs.forEach { (k, v) ->
            when (v) {
                is String -> set(k, v)
                is Long -> set(k, v)
                is Int -> set(k, v.toLong())
                is Double -> set(k, v)
                is Instant -> set(k, v)
                is UUID -> set(k, v)
                is Boolean -> set(k, v)
                null -> set(k, v)
                else -> throw IllegalArgumentException("Unsupported type ${v::class}")
            }
        }
    }