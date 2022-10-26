package processm.core.log.attribute

import processm.core.helpers.mapToSet
import processm.core.log.isAllowedAttributeValue
import java.time.Instant
import java.util.*


internal class MutableAttributeMapWithPrefix(
    flat: SortedMap<CharSequence, Any?> = TreeMap(),
    private val commonPrefix: CharSequence
) : MutableAttributeMap(flat) {

    private fun valueKey(key: CharSequence): CharSequence = LazyString(commonPrefix, key)

    private fun childrenKey(key: CharSequence): CharSequence =
        LazyString(commonPrefix, AttributeMap.SEPARATOR, key.ifEmpty { AttributeMap.EMPTY_KEY }, AttributeMap.SEPARATOR)

    private fun strip(key: CharSequence): CharSequence {
        //TODO optymalizacja zeby lazystring potrafil efektywnie odcinac swoje prefiksy
        assert(key.length >= commonPrefix.length)
        assert(key.substring(0, commonPrefix.length) == commonPrefix.toString()) {"LHS: '${key.substring(0, commonPrefix.length)}' RHS: '$commonPrefix'"}
        return key.substring(commonPrefix.length)
    }

    private operator fun set(key: CharSequence, value: Any?) {
        require(value.isAllowedAttributeValue())
        flat[valueKey(key)] = value
    }

    override operator fun set(key: CharSequence, value: String?) {
        flat[valueKey(key)] = value
    }

    override operator fun set(key: CharSequence, value: Long) {
        flat[valueKey(key)] = value
    }

    override operator fun set(key: CharSequence, value: Double) {
        flat[valueKey(key)] = value
    }

    override operator fun set(key: CharSequence, value: Instant) {
        flat[valueKey(key)] = value
    }

    override operator fun set(key: CharSequence, value: UUID) {
        flat[valueKey(key)] = value
    }

    override operator fun set(key: CharSequence, value: Boolean) {
        flat[valueKey(key)] = value
    }

    override operator fun set(key: CharSequence, value: List<AttributeMap>) {
        flat[valueKey(key)] = value
    }

    override operator fun get(key: CharSequence): Any? = flat.getValue(valueKey(key))

    override fun getOrNull(key: CharSequence?): Any? = if (key !== null) flat[valueKey(key)] else null
    override val childrenKeys: Set<CharSequence>
        get() {
            val prefix = LazyString(commonPrefix, AttributeMap.SEPARATOR)
            return flat.subMap(prefix, LazyString(prefix, AttributeMap.SEPARATOR)).keys.mapToSet {
                val end = it.indexOf(AttributeMap.SEPARATOR, prefix.length)
                it.substring(prefix.length, end).replace(AttributeMap.EMPTY_KEY, "")
            }
        }

    override fun children(key: CharSequence): MutableAttributeMap {
        val s = childrenKey(key)
        return MutableAttributeMapWithPrefix(
            flat.subMap(
                s,
                LazyString(s, AttributeMap.SEPARATOR, AttributeMap.SEPARATOR)
            ), s
        )
    }

    override val top: MutableMap<CharSequence, Any?>
        get() = flat.subMap(commonPrefix, LazyString(commonPrefix, AttributeMap.SEPARATOR))

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

    private class RewritingEntry<V>(
        val base: MutableMap.MutableEntry<CharSequence, V>,
        val from: (CharSequence) -> CharSequence
    ) :
        MutableMap.MutableEntry<CharSequence, V> by base {
        override val key: CharSequence
            get() = from(base.key)
    }

    override val entries: Set<Map.Entry<CharSequence, Any?>>
        get() = RewritingSet(top.entries, { RewritingEntry(it, ::strip) }, { (it as RewritingEntry).base })
    override val keys: Set<CharSequence>
        get() = RewritingSet(top.keys, ::strip) { LazyString(commonPrefix, it) }

    override fun containsKey(key: CharSequence): Boolean = top.containsKey(valueKey(key))

}