package processm.core.log.attribute

import processm.core.helpers.mapToSet
import processm.core.log.attribute.AttributeMap.Companion.EMPTY_KEY
import processm.core.log.attribute.AttributeMap.Companion.SEPARATOR
import processm.core.log.isAllowedAttributeValue
import java.time.Instant
import java.util.*
import kotlin.Comparator


object LazyStringComparator : Comparator<CharSequence> {
    override fun compare(o1: CharSequence, o2: CharSequence): Int {
        if (o1 !is LazyString && o2 !is LazyString) {
            return o1.toString().compareTo(o2.toString())
        }
        println("'${o1.toString()}' '${o2.toString()}' (expected: ${o1.toString().compareTo(o2.toString())})")
        val left = (if (o1 is LazyString) o1.backend else arrayOf(o1)).iterator()
        val right = (if (o2 is LazyString) o2.backend else arrayOf(o2)).iterator()
        while (left.hasNext() && right.hasNext()) {
            val l = left.next()
            val r= right.next()
            val d = compare(l, r)
            println("($l, $r) -> $d")
            //TODO this doesn't work, it may be the case that l is a prefix of r, yet l+left.next() is after r
            if (d != 0)
                return d.also { println("'${o1.toString()}' '${o2.toString()}' -> $it SHORT") }
        }
        return (if (!left.hasNext()) {
            if (!right.hasNext())
                0
            else
                -1
        } else 1).also { println("'${o1.toString()}' '${o2.toString()}' -> $it LONG") }
    }

}

/**
 * Attention! [get] throws if the key is not in the map instead of returning `null`.
 * Similarily, [computeIfAbsent] assigns `null` instead of ignoring it
 */
open class MutableAttributeMap(
    override val flat: SortedMap<CharSequence, Any?> = TreeMap(LazyStringComparator)
) : AttributeMap {

    constructor(map: AttributeMap) : this() {
        map.deepCopyTo(this)
    }

    constructor(map: Map<String, *>) : this() {
        map.entries.forEach { this[it.key] = it.value }
    }

    private fun childrenKey(key: CharSequence): CharSequence =
        SEPARATOR + key.ifEmpty { EMPTY_KEY } + SEPARATOR


    private operator fun set(key: CharSequence, value: Any?) {
        require(value.isAllowedAttributeValue())
        flat[key] = value
    }

    open operator fun set(key: CharSequence, value: String?) {
        flat[key] = value
    }

    open operator fun set(key: CharSequence, value: Long) {
        flat[key] = value
    }

    open operator fun set(key: CharSequence, value: Double) {
        flat[key] = value
    }

    open operator fun set(key: CharSequence, value: Instant) {
        flat[key] = value
    }

    open operator fun set(key: CharSequence, value: UUID) {
        flat[key] = value
    }

    open operator fun set(key: CharSequence, value: Boolean) {
        flat[key] = value
    }

    open operator fun set(key: CharSequence, value: List<AttributeMap>) {
        flat[key] = value
    }

    override operator fun get(key: CharSequence): Any? = flat.getValue(key)

    override fun getOrNull(key: CharSequence?): Any? = if (key !== null) flat[key] else null
    override val childrenKeys: Set<CharSequence>
        get() {
            val prefix = SEPARATOR
            return flat.subMap(prefix, prefix + SEPARATOR).keys.mapToSet {
                val end = it.indexOf(SEPARATOR, prefix.length)
                it.substring(prefix.length, end).replace(EMPTY_KEY, "")
            }
        }

    override fun children(key: CharSequence): MutableAttributeMap {
        val s = childrenKey(key)
        return MutableAttributeMapWithPrefix(flat.subMap(s, LazyString(s, SEPARATOR, SEPARATOR)), s)
    }

    protected open val top: MutableMap<CharSequence, Any?>
        get() = flat.headMap(SEPARATOR)

    override val entries: Set<Map.Entry<CharSequence, Any?>>
        get() = top.entries

    override val keys: Set<CharSequence>
        get() = top.keys

    override val size: Int
        get() = top.size

    override val values: Collection<Any?>
        get() = top.values

    override fun isEmpty(): Boolean = top.isEmpty()
    override fun containsValue(value: Any?): Boolean = top.containsValue(value)

    override fun containsKey(key: CharSequence): Boolean = top.containsKey(key)

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
                is List<*> -> set(k, v as List<AttributeMap>)
                is Instant -> set(k, v)
                is UUID -> set(k, v)
                is Boolean -> set(k, v)
                null -> set(k, v)
                else -> throw IllegalArgumentException("Unsupported type ${v::class}")
            }
        }
    }