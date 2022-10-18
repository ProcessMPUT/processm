package processm.core.log

import processm.core.helpers.mapToSet
import processm.core.log.attribute.Attribute
import java.util.*

class AttributeMap<V>(
    val flat: SortedMap<String, V> = TreeMap(),
    private val commonPrefix: String = ""
) : Map<String, V> {

    constructor(map: Map<String, V>) : this() {
        map.entries.forEach { this[it.key] = it.value }
    }

    private data class MyEntry<V>(override val key: String, override val value: V) : Map.Entry<String, V>

    companion object {
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

    operator fun set(key: List<String>, value: V) {
        flat[valueKey(key)] = value
    }

    operator fun set(key: String, value: V) = set(listOf(key), value)

    override operator fun get(key: String): V? = flat[valueKey(key)]

    operator fun get(key: List<String>): V? = flat[valueKey(key)]

    fun children(key: String): AttributeMap<V> = children(listOf(key))

    fun children(key: List<String>): AttributeMap<V> {
        require(key.isNotEmpty())
        val s = childrenKey(key)
        return AttributeMap(flat.subMap(s, s + SEPARATOR + SEPARATOR), s)
    }

    private val top: Map<String, V> by lazy { flat.subMap(commonPrefix, commonPrefix + SEPARATOR) }

    override val entries: Set<Map.Entry<String, V>>
        get() = top.entries.mapToSet { MyEntry(strip(it.key), it.value) }
    override val keys: Set<String>
        get() = top.keys.mapToSet(::strip)
    override val size: Int
        get() = top.size
    override val values: Collection<V>
        get() = top.values

    override fun isEmpty(): Boolean = top.isEmpty()

    override fun containsValue(value: V): Boolean = top.containsValue(value)

    override fun containsKey(key: String): Boolean = top.containsKey(valueKey(key))

    fun computeIfAbsent(key: String, ctor: (key: String) -> V?): V? =
        flat.computeIfAbsent(valueKey(key), ctor)

    override fun equals(other: Any?): Boolean {
        if (other is AttributeMap<*>) {
            return this.flat == other.flat
        }
        if (other is Map<*, *>) {
            return this.flat == other
        }
        return false
    }
}