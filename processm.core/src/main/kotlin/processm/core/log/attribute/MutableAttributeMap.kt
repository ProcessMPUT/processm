package processm.core.log.attribute

import processm.core.helpers.mapToSet
import processm.core.log.attribute.AttributeMap.Companion.EMPTY_KEY
import processm.core.log.attribute.AttributeMap.Companion.SEPARATOR
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

    private fun childrenKey(key: String): String =
        SEPARATOR + key.ifEmpty { EMPTY_KEY } + SEPARATOR


    private operator fun set(key: String, value: Any?) {
        require(value.isAllowedAttributeValue())
        flat[key] = value
    }

    open operator fun set(key: String, value: String?) {
        flat[key] = value
    }

    open operator fun set(key: String, value: Long) {
        flat[key] = value
    }

    open operator fun set(key: String, value: Double) {
        flat[key] = value
    }

    open operator fun set(key: String, value: Instant) {
        flat[key] = value
    }

    open operator fun set(key: String, value: UUID) {
        flat[key] = value
    }

    open operator fun set(key: String, value: Boolean) {
        flat[key] = value
    }

    open operator fun set(key: String, value: List<AttributeMap>) {
        flat[key] = value
    }

    override operator fun get(key: String): Any? = flat.getValue(key)

    override fun getOrNull(key: String?): Any? = if (key !== null) flat[key] else null
    override val childrenKeys: Set<String>
        get() {
            val prefix = SEPARATOR
            return flat.subMap(prefix, prefix + SEPARATOR).keys.mapToSet {
                val end = it.indexOf(SEPARATOR, prefix.length)
                it.substring(prefix.length, end).replace(EMPTY_KEY, "")
            }
        }

    override fun children(key: String): MutableAttributeMap {
        val s = childrenKey(key)
        return MutableAttributeMapWithPrefix(flat.subMap(s, s + SEPARATOR + SEPARATOR), s)
    }

    protected open val top: MutableMap<String, Any?>
        get() = flat.headMap(SEPARATOR)

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
                is List<*> -> set(k, v as List<AttributeMap>)
                is Instant -> set(k, v)
                is UUID -> set(k, v)
                is Boolean -> set(k, v)
                null -> set(k, v)
                else -> throw IllegalArgumentException("Unsupported type ${v::class}")
            }
        }
    }