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
import kotlin.Comparator

/**
 * Attention! [get] throws if the key is not in the map instead of returning `null`.
 * Similarly, [computeIfAbsent] assigns `null` instead of ignoring it
 *
 * @param intern A possible replacement for [String.intern], e.g., a more efficient implementation, or an idempotent function to disable interning
 */
open class MutableAttributeMap(
    protected val flat: SortedMap<CharSequence, Any?> = TreeMap(comparator),
    val intern: (String) -> String = String::intern
) : AttributeMap {

    companion object {
        val comparator = Comparator<CharSequence> { a, b ->
            if (a is SemiRope) a.compareTo(b)
            else if (b is SemiRope) -b.compareTo(a)
            else {
                check(a is String)
                check(b is String)
                a.compareTo(b)
            }
        }
    }

    protected open val stringPrefix: CharSequence
        get() = BEFORE_STRING

    protected open val intPrefix: CharSequence
        get() = BEFORE_INT

    constructor(map: AttributeMap) : this(intern = if (map is MutableAttributeMap) map.intern else String::intern) {
        flat.putAll(map.flatView)
    }

    private val children = HashMap<Any, Pair<CharSequence, CharSequence>>()

    protected open fun childrenRange(key: String): Pair<CharSequence, CharSequence> {
        require(SEPARATOR_CHAR !in key)
        return children.computeIfAbsent(key) {
            val prefix = SemiRope(stringPrefix, key)
            SemiRope(prefix, SEPARATOR) to SemiRope(prefix, AFTER_SEPARATOR)
        }
    }

    protected open fun childrenRange(key: Int): Pair<CharSequence, CharSequence> {
        return children.computeIfAbsent(key) {
            val prefix = SemiRope(intPrefix, key.toString())
            SemiRope(prefix, SEPARATOR) to SemiRope(prefix, AFTER_SEPARATOR)
        }
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
    override val flatView: Map<CharSequence, Any?>
        get() = Collections.unmodifiableMap(flat)

    override fun getOrNull(key: String?): Any? = if (key !== null) flat[key] else null

    protected fun childrenKeys(prefix: CharSequence): Set<Any> {
        val prefixWithSeparatorLength = prefix.length + 1
        return flat.subMap(SemiRope(prefix, SEPARATOR), SemiRope(prefix, AFTER_SEPARATOR)).keys.mapToSet {
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

    protected open val top: MutableMap<CharSequence, Any?> by lazy(LazyThreadSafetyMode.NONE) {
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