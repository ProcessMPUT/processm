package processm.core.log.attribute

import processm.core.log.attribute.AttributeMap.Companion.BEFORE_INT
import processm.core.log.attribute.AttributeMap.Companion.BEFORE_STRING
import processm.core.log.attribute.AttributeMap.Companion.INT_MARKER
import processm.core.log.attribute.AttributeMap.Companion.STRING_MARKER
import processm.core.log.isAllowedAttributeValue
import java.lang.ref.SoftReference
import java.time.Instant
import java.util.*


internal class MutableAttributeMapWithPrefix(
    flat: SortedMap<CharSequence, Any?>,
    private val commonPrefix: CharSequence,
    intern: (String) -> String
) : MutableAttributeMap(flat, intern = intern) {
    private fun valueKey(key: String): CharSequence = SemiRope(commonPrefix, key)

    override val stringPrefix: CharSequence by lazy(LazyThreadSafetyMode.NONE) {
        SemiRope(commonPrefix, BEFORE_STRING)
    }

    override val intPrefix: CharSequence by lazy(LazyThreadSafetyMode.NONE) {
        SemiRope(commonPrefix, BEFORE_INT)
    }

    private fun strip(key: CharSequence): String {
        require(key is SemiRope)
        return key.right
    }

    private inline fun unsafeSet(key: String, value: Any?) {
        require(AttributeMap.SEPARATOR_CHAR !in key)
        flat[valueKey(intern(key))] = value
    }

    /**
    Since unsafeSet is private inline, this is not the same as [MutableAttributeMap.safeSet] even though it looks the same
     */
    override fun safeSet(key: String, value: Any?) {
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

    override val top: MutableMap<CharSequence, Any?> by lazy(LazyThreadSafetyMode.NONE) {
        //TODO or SemiRope?
        val commonPrefix = this.commonPrefix.toString()
        val leftEnd = commonPrefix + AttributeMap.SEPARATOR_CHAR
        val rightStart = commonPrefix + AttributeMap.AFTER_SEPARATOR_CHAR
        val rightEnd = commonPrefix.substring(0, commonPrefix.length - 1) + AttributeMap.AFTER_SEPARATOR_CHAR
        SplitMutableMap(
            flat.subMap(commonPrefix, leftEnd),
            flat.subMap(rightStart, rightEnd),
            rightStart,
            comparator
        )
    }

    private class RewritingIterator<T1, T2>(val baseIterator: Iterator<T2>, val from: (T2) -> T1) :
        Iterator<T1> {
        override fun hasNext(): Boolean = baseIterator.hasNext()

        override fun next(): T1 = from(baseIterator.next())
    }

    private class RewritingSet<E1, E2>(val base: Set<E2>, val from: (E2) -> E1, val to: (E1) -> E2) :
        Set<E1> {

        override val size: Int
            get() = base.size

        override fun isEmpty(): Boolean = base.isEmpty()

        override fun containsAll(elements: Collection<E1>): Boolean = base.containsAll(elements.map(to))

        override fun contains(element: E1): Boolean = base.contains(to(element))

        override fun iterator(): Iterator<E1> = RewritingIterator(base.iterator(), from)

    }

    private class RewritingEntry<E1, E2, V>(val base: MutableMap.MutableEntry<E1, V>, val from: (E1) -> E2) :
        MutableMap.MutableEntry<E2, V> {
        override val key: E2
            get() = from(base.key)
        override val value: V
            get() = base.value

        override fun setValue(newValue: V): V = base.setValue(newValue)
    }

    override val entries: Set<Map.Entry<String, Any?>>
        get() = RewritingSet(top.entries, { RewritingEntry(it, ::strip) }, { (it as RewritingEntry).base })
    override val keys: Set<String>
        get() = RewritingSet(top.keys, ::strip) { SemiRope(commonPrefix, it) }

    override fun containsKey(key: String): Boolean = top.containsKey(valueKey(key))

}