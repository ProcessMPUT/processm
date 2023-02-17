package processm.core.log.attribute

import processm.core.log.attribute.AttributeMap.Companion.INT_MARKER
import processm.core.log.attribute.AttributeMap.Companion.STRING_MARKER
import processm.core.log.isAllowedAttributeValue
import java.time.Instant
import java.util.*


internal class MutableAttributeMapWithPrefix(
    flat: SortedMap<String, Any?>,
    private val commonPrefix: String
) : MutableAttributeMap(flat) {

    private fun valueKey(key: String): String = commonPrefix + key

    override fun childrenKey(key: String): Pair<String, String> {
        require(AttributeMap.SEPARATOR_CHAR !in key)
        val prefix = commonPrefix + AttributeMap.SEPARATOR + STRING_MARKER + key
        return prefix + AttributeMap.SEPARATOR to prefix + AttributeMap.AFTER_SEPARATOR_CHAR
    }

    override fun childrenKey(key: Int): Pair<String, String> {
        val prefix = commonPrefix + AttributeMap.SEPARATOR + INT_MARKER + key
        return prefix + AttributeMap.SEPARATOR to prefix + AttributeMap.AFTER_SEPARATOR_CHAR
    }

    private fun strip(key: String): String {
        assert(key.length >= commonPrefix.length)
        assert(key.substring(0, commonPrefix.length) == commonPrefix)
        return key.substring(commonPrefix.length)
    }

    private inline fun unsafeSet(key: String, value: Any?) {
        require(AttributeMap.SEPARATOR_CHAR !in key)
        flat[valueKey(key)] = value
    }

    private operator fun set(key: String, value: Any?) {
        require(value.isAllowedAttributeValue())
        unsafeSet(key, value)
    }

    override operator fun set(key: String, value: String?) {
        unsafeSet(key, value)
    }

    override operator fun set(key: String, value: Long) {
        unsafeSet(key, value)
    }

    override operator fun set(key: String, value: Double) {
        unsafeSet(key, value)
    }

    override operator fun set(key: String, value: Instant) {
        unsafeSet(key, value)
    }

    override operator fun set(key: String, value: UUID) {
        unsafeSet(key, value)
    }

    override operator fun set(key: String, value: Boolean) {
        unsafeSet(key, value)
    }

    override operator fun set(key: String, value: Tag) {
        unsafeSet(key, value)
    }

    override operator fun get(key: String): Any? = flat.getValue(valueKey(key))

    override fun getOrNull(key: String?): Any? = if (key !== null) flat[valueKey(key)] else null
    override val childrenKeys: Set<Any>
        get() = childrenKeys(commonPrefix)

    override val top: MutableMap<String, Any?>

    init {
        val leftEnd = commonPrefix + AttributeMap.SEPARATOR_CHAR
        val rightStart = commonPrefix + AttributeMap.AFTER_SEPARATOR_CHAR
        val rightEnd = commonPrefix.substring(0, commonPrefix.length - 1) + AttributeMap.AFTER_SEPARATOR_CHAR
        top = SplitMutableMap(flat.subMap(commonPrefix, leftEnd), flat.subMap(rightStart, rightEnd), rightStart)
    }

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