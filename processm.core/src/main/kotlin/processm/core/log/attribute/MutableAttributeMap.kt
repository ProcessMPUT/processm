package processm.core.log.attribute

import processm.core.helpers.mapToSet
import processm.core.log.attribute.AttributeMap.Companion.AFTER_SEPARATOR_CHAR
import processm.core.log.attribute.AttributeMap.Companion.INT_MARKER
import processm.core.log.attribute.AttributeMap.Companion.SEPARATOR
import processm.core.log.attribute.AttributeMap.Companion.SEPARATOR_CHAR
import processm.core.log.attribute.AttributeMap.Companion.STRING_MARKER
import processm.core.log.isAllowedAttributeValue
import java.time.Instant
import java.util.*

/**
 * Attention! [get] throws if the key is not in the map instead of returning `null`.
 * Similarily, [computeIfAbsent] assigns `null` instead of ignoring it
 */
open class MutableAttributeMap(
    override val flat: SortedMap<String, Any?> = TreeMap()
) : AttributeMap {

    constructor(map: AttributeMap) : this() {
        map.deepCopyTo(this)
    }

    constructor(map: Map<String, *>) : this() {
        map.entries.forEach { this[it.key] = it.value }
    }

    protected open fun childrenKey(key: String): Pair<String, String> {
        require(SEPARATOR_CHAR !in key)
        val prefix = SEPARATOR + STRING_MARKER + key
        return prefix + SEPARATOR to prefix + AFTER_SEPARATOR_CHAR
    }

    protected open fun childrenKey(key: Int): Pair<String, String> {
        val prefix = SEPARATOR + INT_MARKER + key
        return prefix + SEPARATOR to prefix + AFTER_SEPARATOR_CHAR
    }

    private inline fun unsafeSet(key: String, value: Any?) {
        require(SEPARATOR_CHAR !in key)
        flat[key] = value
    }

    private operator fun set(key: String, value: Any?) {
        require(value.isAllowedAttributeValue())
        unsafeSet(key, value)
    }

    open operator fun set(key: String, value: String?) {
        unsafeSet(key, value)
    }

    open operator fun set(key: String, value: Long) {
        unsafeSet(key, value)
    }

    open operator fun set(key: String, value: Double) {
        unsafeSet(key, value)
    }

    open operator fun set(key: String, value: Instant) {
        unsafeSet(key, value)
    }

    open operator fun set(key: String, value: UUID) {
        unsafeSet(key, value)
    }

    open operator fun set(key: String, value: Boolean) {
        unsafeSet(key, value)
    }

    open operator fun set(key: String, value: Tag) {
        unsafeSet(key, value)
    }

    override operator fun get(key: String): Any? = flat.getValue(key)

    override fun getOrNull(key: String?): Any? = if (key !== null) flat[key] else null

    protected fun childrenKeys(prefix: String): Set<Any> {
        val prefixWithSeparatorLength = prefix.length + 1
        return flat.subMap(prefix + SEPARATOR_CHAR, prefix + AFTER_SEPARATOR_CHAR).keys.mapToSet {
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
        val (s, e) = childrenKey(key)
        return MutableAttributeMapWithPrefix(flat.subMap(s, e), s)
    }

    override fun children(key: Int): MutableAttributeMap {
        val (s, e) = childrenKey(key)
        return MutableAttributeMapWithPrefix(flat.subMap(s, e), s)
    }

    protected open val top: MutableMap<String, Any?> by lazy(LazyThreadSafetyMode.NONE) {
        // lazy initialization because taking head/tail map of an empty map doesn't seem to work
        SplitMutableMap(flat.headMap(SEPARATOR), flat.tailMap(AFTER_SEPARATOR_CHAR), SEPARATOR)
    }

    override val entries: Set<Map.Entry<String, Any?>>
        get() = top.entries

    override val keys: Set<String>
        get() = top.keys

    override val size: Int
        get() = top.size

    override val values: Collection<Any?>
        get() = top.values

    override fun isEmpty(): Boolean = top.isEmpty()
    override fun containsValue(value: Any?): Boolean = top.containsValue(value)

    override fun containsKey(key: String): Boolean = top.containsKey(key)

    fun computeIfAbsent(key: String, ctor: (key: String) -> Any?): Any? =
        if (!containsKey(key)) {
            val value = ctor(key)
            set(key, value)
            value
        } else
            get(key)

    override fun equals(other: Any?): Boolean {
        if (other is MutableAttributeMap) {
            return this.flat == other.flat
        }
        if (other is Map<*, *>) {
            return this.flat == other
        }
        return false
    }
}

fun AttributeMap.toMutableAttributeMap() = MutableAttributeMap(this)

fun AttributeMap.deepCopyTo(destination: MutableAttributeMap) {
    destination.flat.putAll(flat)
}

fun attributeMapOf(vararg pairs: Pair<String, Any?>): AttributeMap = mutableAttributeMapOf(*pairs)
fun mutableAttributeMapOf(vararg pairs: Pair<String, Any?>): MutableAttributeMap =
    MutableAttributeMap().apply {
        pairs.forEach { (k, v) ->
            when (v) {
                is String -> set(k, v)
                is Long -> set(k, v)
                is Double -> set(k, v)
                is Instant -> set(k, v)
                is UUID -> set(k, v)
                is Boolean -> set(k, v)
                null -> set(k, v)
                else -> throw IllegalArgumentException("Unsupported type ${v::class}")
            }
        }
    }