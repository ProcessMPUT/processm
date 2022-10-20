package processm.core.log

import processm.core.helpers.mapToSet
import processm.core.log.attribute.Attribute
import java.time.Instant
import java.util.*

/**
 * Attention! [get] throws if the key is not in the map instead of returning `null`.
 * Similarily, [computeIfAbsent] assigns `null` instead of ignoring it
 */
class AttributeMap(
    val flat: SortedMap<String, Any?> = TreeMap(),
    private val commonPrefix: String = ""
) : Map<String, Any?> {

    constructor(map: Map<String, *>) : this() {
        map.entries.forEach { this[it.key] = it.value }
    }

    private data class MyEntry<V>(override val key: String, override val value: V) : Map.Entry<String, V>

    companion object {
        //TODO revisit values, possibly ensure that keys supplied by the user don't use character above these two
        const val EMPTY_KEY = "\uc07f"
        const val SEPARATOR = "\uc080"
    }

    private fun valueKey(key: List<String>): String {
        require(key.isNotEmpty())
        return childrenKey(key.subList(0, key.size - 1)) + key.last()
    }

    private fun valueKey(key: String): String = commonPrefix + key

    private fun childrenKey(key: List<String>): String =
        commonPrefix + key.joinToString(separator = "") { SEPARATOR + it.ifEmpty { EMPTY_KEY } + SEPARATOR }

    private fun strip(key: String): String {
        assert(key.length >= commonPrefix.length)
        assert(key.substring(0, commonPrefix.length) == commonPrefix)
        return key.substring(commonPrefix.length)
    }

    operator fun set(key: List<String>, value: Any?) {
        require(value.isAllowedAttributeValue())
        flat[valueKey(key)] = value
    }

    operator fun set(key: String, value: Any?) = set(listOf(key), value)

    override operator fun get(key: String): Any? = flat.getValue(valueKey(key))

    fun getOrNull(key: String?): Any? = if (key !== null) flat[valueKey(key)] else null

    operator fun get(key: List<String>): Any? = flat.getValue(valueKey(key))

    fun children(key: String): AttributeMap = children(listOf(key))

    fun children(key: List<String>): AttributeMap {
        require(key.isNotEmpty())
        val s = childrenKey(key)
        return AttributeMap(flat.subMap(s, s + SEPARATOR + SEPARATOR), s)
    }

    private val top: Map<String, Any?> by lazy { flat.subMap(commonPrefix, commonPrefix + SEPARATOR) }

    override val entries: Set<Map.Entry<String, Any?>>
        get() = top.entries.mapToSet { MyEntry(strip(it.key), it.value) }
    override val keys: Set<String>
        get() = top.keys.mapToSet(::strip)
    override val size: Int
        get() = top.size
    override val values: Collection<Any?>
        get() = top.values

    override fun isEmpty(): Boolean = top.isEmpty()

    override fun containsValue(value: Any?): Boolean = top.containsValue(value)

    override fun containsKey(key: String): Boolean = top.containsKey(valueKey(key))

    fun computeIfAbsent(key: String, ctor: (key: String) -> Any?): Any? =
        if (!containsKey(key)) {
            val value = ctor(key)
            set(key, value)
            value
        } else
            get(key)

    override fun equals(other: Any?): Boolean {
        if (other is AttributeMap) {
            return this.flat == other.flat
        }
        if (other is Map<*, *>) {
            return this.flat == other
        }
        return false
    }
}