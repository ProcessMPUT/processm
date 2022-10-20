package processm.core.log

import processm.core.helpers.mapToSet
import java.time.Instant
import java.util.*

/**
 * Attention! [get] throws if the key is not in the map instead of returning `null`.
 * Similarily, [computeIfAbsent] assigns `null` instead of ignoring it
 */
class MutableAttributeMap(
    val flat: SortedMap<String, Any?> = TreeMap(),
    private val commonPrefix: String = ""
) : AttributeMap {

    companion object {
        //TODO revisit values, possibly ensure that keys supplied by the user don't use character above these two
        const val EMPTY_KEY = "\uc07f"
        const val SEPARATOR = "\uc080"
    }

    constructor(map: AttributeMap) : this() {
        map.deepCopyTo(this)
    }

    constructor(map: Map<String, *>) : this() {
        map.entries.forEach { this[it.key] = it.value }
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

    internal operator fun set(key: List<String>, value: Any?) {
        require(value.isAllowedAttributeValue())
        flat[valueKey(key)] = value
    }

    internal operator fun set(key: String, value: Any?) {
        require(value.isAllowedAttributeValue())
        flat[valueKey(key)] = value
    }

    operator fun set(key: String, value: String?) {
        flat[valueKey(key)] = value
    }

    operator fun set(key: String, value: Long) {
        flat[valueKey(key)] = value
    }

    operator fun set(key: String, value: Double) {
        flat[valueKey(key)] = value
    }

    operator fun set(key: String, value: Instant) {
        flat[valueKey(key)] = value
    }

    operator fun set(key: String, value: UUID) {
        flat[valueKey(key)] = value
    }

    operator fun set(key: String, value: Boolean) {
        flat[valueKey(key)] = value
    }

    operator fun set(key: String, value: List<AttributeMap>) {
        flat[valueKey(key)] = value
    }

    override operator fun get(key: String): Any? = flat.getValue(valueKey(key))

    override fun getOrNull(key: String?): Any? = if (key !== null) flat[valueKey(key)] else null

    override operator fun get(key: List<String>): Any? = flat.getValue(valueKey(key))

    override val childrenKeys: Set<String>
        get() {
            val prefix = commonPrefix + SEPARATOR
            return flat.subMap(prefix, prefix + SEPARATOR).keys.mapToSet {
                val end = it.indexOf(SEPARATOR, prefix.length)
                it.substring(prefix.length, end)
            }
        }

    override fun children(key: String): MutableAttributeMap = children(listOf(key))

    override fun children(key: List<String>): MutableAttributeMap {
        require(key.isNotEmpty())
        val s = childrenKey(key)
        return MutableAttributeMap(flat.subMap(s, s + SEPARATOR + SEPARATOR), s)
    }

    private val top: MutableMap<String, Any?> by lazy { flat.subMap(commonPrefix, commonPrefix + SEPARATOR) }

    private class RewritingIterator<T>(val baseIterator: MutableIterator<T>, val from: (T) -> T) :
        MutableIterator<T> by baseIterator {
        override fun next(): T = from(baseIterator.next())
    }

    private class RewritingMutableSet<E>(val base: MutableSet<E>, val from: (E) -> E, val to: (E) -> E) :
        MutableSet<E> {
        override fun add(element: E): Boolean = base.add(to(element))

        override fun addAll(elements: Collection<E>): Boolean = base.addAll(elements.map(to))

        override val size: Int
            get() = base.size

        override fun clear() = base.clear()

        override fun isEmpty(): Boolean = base.isEmpty()

        override fun containsAll(elements: Collection<E>): Boolean = base.containsAll(elements.map(to))

        override fun contains(element: E): Boolean = base.contains(to(element))

        override fun iterator(): MutableIterator<E> = RewritingIterator(base.iterator(), from)

        override fun retainAll(elements: Collection<E>): Boolean = base.retainAll(elements.mapToSet(to))

        override fun removeAll(elements: Collection<E>): Boolean = base.removeAll(elements.mapToSet(to))

        override fun remove(element: E): Boolean = base.remove(to(element))

    }

    private class RewritingEntry<V>(val base: MutableMap.MutableEntry<String, V>, val from: (String) -> String) :
        MutableMap.MutableEntry<String, V> by base {
        override val key: String
            get() = from(base.key)
    }

    override val entries: MutableSet<MutableMap.MutableEntry<String, Any?>>
        get() = RewritingMutableSet(top.entries, { RewritingEntry(it, ::strip) }, { (it as RewritingEntry).base })
    override val keys: MutableSet<String>
        get() = RewritingMutableSet(top.keys, ::strip) { commonPrefix + it }
    override val size: Int
        get() = top.size
    override val values: MutableCollection<Any?>
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
    for ((k, v) in entries)
        destination[k] = v
    for (key in childrenKeys)
        children(key).deepCopyTo(destination.children(key))
}

fun attributeMapOf(vararg pairs: Pair<String, Any?>): AttributeMap = mutableAttributeMapOf(*pairs)
fun mutableAttributeMapOf(vararg pairs: Pair<String, Any?>): MutableAttributeMap =
    MutableAttributeMap().apply { pairs.forEach { (k, v) -> set(k, v) } }