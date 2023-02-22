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
 * Attention! [get] throws if the key is not in the map instead of returning `null`.
 * Similarly, [computeIfAbsent] assigns `null` instead of ignoring it
 *
 * @param intern A possible replacement for [String.intern], e.g., a more efficient implementation, or an idempotent function to disable interning
 */
open class MutableAttributeMap(
    protected val flat: SortedMap<String, Any?> = TreeMap(),
    val intern: (String) -> String = String::intern
) : AttributeMap {

    companion object {
        val comparator = Comparator<String> { a, b ->
            a.compareTo(b)
        }
    }

    protected open val stringPrefix: String
        get() = BEFORE_STRING

    protected open val intPrefix: String
        get() = BEFORE_INT

    constructor(map: AttributeMap) : this(intern = if (map is MutableAttributeMap) map.intern else String::intern) {
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
        flat[intern(key)] = value
    }

    protected open fun safeSet(key: String, value: Any?) {
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
        val (s, e) = childrenRange(intern(key))
        return MutableAttributeMapWithPrefix(flat.subMap(s, e), s, intern)
    }

    override fun children(key: Int): MutableAttributeMap {
        val (s, e) = childrenRange(key)
        return MutableAttributeMapWithPrefix(flat.subMap(s, e), s, intern)
    }

    protected open val top: MutableMap<String, Any?> by lazy(LazyThreadSafetyMode.NONE) {
        // lazy initialization because taking head/tail map of an empty map doesn't seem to work
        SplitMutableMap(flat.headMap(SEPARATOR), flat.tailMap(AFTER_SEPARATOR), SEPARATOR, comparator)
    }

    override val entries: Set<Map.Entry<String, Any?>>
        get() = top.entries as Set<Map.Entry<String, Any?>>

    override val keys: Set<String>
        get() = top.keys as Set<String>

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
            safeSet(key, value)
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

    fun putFlat(key: String, value: Any) {
        require(value.isAllowedAttributeValue())
        var target = this
        var start = 0
        while (key[start] == SEPARATOR_CHAR) {
            val end = key.indexOf(SEPARATOR_CHAR, startIndex = start + 1)
            val childKey = key.substring(start + 2, end)
            target =
                if (key[start + 1] == STRING_MARKER) target.children(childKey) else target.children(childKey.toInt())
            start = end + 1
        }
        target.safeSet(key.substring(start), value)
    }
}

fun AttributeMap.toMutableAttributeMap() = MutableAttributeMap(this)

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