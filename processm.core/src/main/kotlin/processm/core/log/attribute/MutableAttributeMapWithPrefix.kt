package processm.core.log.attribute

import processm.core.helpers.mapToSet
import processm.core.log.isAllowedAttributeValue
import java.time.Instant
import java.util.*


internal class MutableAttributeMapWithPrefix(
    flat: SortedMap<String, Any?> = TreeMap(),
    private val commonPrefix: String
) : MutableAttributeMap(flat) {

    private fun valueKey(key: String): String = commonPrefix + key

    private fun childrenKey(key: String): String =
        commonPrefix + AttributeMap.SEPARATOR + key.ifEmpty { AttributeMap.EMPTY_KEY } + AttributeMap.SEPARATOR

    private fun strip(key: String): String {
        assert(key.length >= commonPrefix.length)
        assert(key.substring(0, commonPrefix.length) == commonPrefix)
        return key.substring(commonPrefix.length)
    }

    private operator fun set(key: String, value: Any?) {
        require(value.isAllowedAttributeValue())
        flat[valueKey(key)] = value
    }

    override operator fun set(key: String, value: String?) {
        flat[valueKey(key)] = value
    }

    override operator fun set(key: String, value: Long) {
        flat[valueKey(key)] = value
    }

    override operator fun set(key: String, value: Double) {
        flat[valueKey(key)] = value
    }

    override operator fun set(key: String, value: Instant) {
        flat[valueKey(key)] = value
    }

    override operator fun set(key: String, value: UUID) {
        flat[valueKey(key)] = value
    }

    override operator fun set(key: String, value: Boolean) {
        flat[valueKey(key)] = value
    }

    override operator fun set(key: String, value: List<AttributeMap>) {
        flat[valueKey(key)] = value
    }

    override operator fun get(key: String): Any? = flat.getValue(valueKey(key))

    override fun getOrNull(key: String?): Any? = if (key !== null) flat[valueKey(key)] else null
    override val childrenKeys: Set<String>
        get() {
            val prefix = commonPrefix + AttributeMap.SEPARATOR
            return flat.subMap(prefix, prefix + AttributeMap.SEPARATOR).keys.mapToSet {
                val end = it.indexOf(AttributeMap.SEPARATOR, prefix.length)
                it.substring(prefix.length, end).replace(AttributeMap.EMPTY_KEY, "")
            }
        }

    override fun children(key: String): MutableAttributeMap {
        val s = childrenKey(key)
        return MutableAttributeMapWithPrefix(flat.subMap(s, s + AttributeMap.SEPARATOR + AttributeMap.SEPARATOR), s)
    }

    override val top: MutableMap<String, Any?>
        get() = flat.subMap(commonPrefix, commonPrefix + AttributeMap.SEPARATOR)

    private class RewritingIterator<T>(val baseIterator: Iterator<T>, val from: (T) -> T) :
        Iterator<T> by baseIterator {
        override fun next(): T = from(baseIterator.next())
    }

    private class RewritingSet<E>(val base: Set<E>, val from: (E) -> E, val to: (E) -> E) :
        Set<E> {

        override val size: Int
            get() = base.size

        override fun isEmpty(): Boolean = base.isEmpty()

        override fun containsAll(elements: Collection<E>): Boolean = base.containsAll(elements.map(to))

        override fun contains(element: E): Boolean = base.contains(to(element))

        override fun iterator(): Iterator<E> = RewritingIterator(base.iterator(), from)

    }

    private class RewritingEntry<V>(val base: MutableMap.MutableEntry<String, V>, val from: (String) -> String) :
        MutableMap.MutableEntry<String, V> by base {
        override val key: String
            get() = from(base.key)
    }

    override val entries: Set<Map.Entry<String, Any?>>
        get() = RewritingSet(top.entries, { RewritingEntry(it, ::strip) }, { (it as RewritingEntry).base })
    override val keys: Set<String>
        get() = RewritingSet(top.keys, ::strip) { commonPrefix + it }

    override fun containsKey(key: String): Boolean = top.containsKey(valueKey(key))

}