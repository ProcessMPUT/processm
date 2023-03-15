package processm.core.log.attribute

import java.util.SortedMap

/**
 * An iterator that first iterates over [left], and then over [right]
 */
private class SplitMutableIterator<T>(val left: MutableIterator<T>, val right: MutableIterator<T>) :
    MutableIterator<T> {
    private var realHasNext: () -> Boolean
    private var realNext: () -> T
    private var realRemove: () -> Unit

    init {
        if (left.hasNext()) {
            realNext = left::next
            realHasNext = ::leftHasNext
            realRemove = left::remove
        } else {
            realHasNext = right::hasNext
            realNext = right::next
            realRemove = right::remove
        }
    }

    private fun leftHasNext(): Boolean {
        if (left.hasNext())
            return true
        realHasNext = right::hasNext
        realNext = right::next
        realRemove = right::remove
        return right.hasNext()
    }

    override fun hasNext(): Boolean = realHasNext()

    override fun next(): T = realNext()

    override fun remove() = realRemove()

}

/**
 * [MutableCollection] view representing a union of [left] and [right]. Adding new elements to the collection is not supported,
 * what is consistent with the behaviour of collections returned by [Map.values]
 */
private open class SplitMutableCollection<T>(
    open val left: MutableCollection<T>,
    open val right: MutableCollection<T>
) : MutableCollection<T> {
    override val size: Int
        get() = left.size + right.size

    override fun clear() {
        left.clear()
        right.clear()
    }

    override fun addAll(elements: Collection<T>): Boolean = throw UnsupportedOperationException()

    override fun add(element: T): Boolean = throw UnsupportedOperationException()

    override fun isEmpty(): Boolean = left.isEmpty() && right.isEmpty()

    override fun iterator(): MutableIterator<T> = SplitMutableIterator(left.iterator(), right.iterator())

    override fun retainAll(elements: Collection<T>): Boolean {
        // Intentionally not inlined to avoid short-circuiting
        val l = left.retainAll(elements)
        val r = right.retainAll(elements)
        return l || r
    }

    override fun removeAll(elements: Collection<T>): Boolean {
        // Intentionally not inlined to avoid short-circuiting
        val l = left.removeAll(elements)
        val r = right.removeAll(elements)
        return l || r
    }

    override fun remove(element: T): Boolean = left.remove(element) || right.remove(element)

    override fun containsAll(elements: Collection<T>): Boolean = elements.all(::contains)

    override fun contains(element: T): Boolean = element in left || element in right

}

private class SplitMutableSet<T>(override val left: MutableSet<T>, override val right: MutableSet<T>) :
    SplitMutableCollection<T>(left, right), MutableSet<T>

/**
 * A map backed by two maps. All keys below [lastLeftKeyExclusive] are stored in [left], and the remainder is stored in [right]
 */
internal class SplitMutableMap<K, V>(
    val left: SortedMap<K, V>,
    val right: SortedMap<K, V>,
    private val lastLeftKeyExclusive: K,
    private val comparator: Comparator<K>
) : MutableMap<K, V> {


    init {
        require(left.isEmpty() || comparator.compare(left.lastKey(), lastLeftKeyExclusive) < 0)
        require(right.isEmpty() || comparator.compare(lastLeftKeyExclusive, right.firstKey()) <= 0)
    }

    override fun containsKey(key: K): Boolean = left.containsKey(key) || right.containsKey(key)

    override fun containsValue(value: V): Boolean = left.containsValue(value) || right.containsValue(value)

    override fun get(key: K): V? = if (comparator.compare(key, lastLeftKeyExclusive) < 0) left[key] else right[key]

    override fun clear() {
        left.clear()
        right.clear()
    }

    override fun remove(key: K): V? =
        if (comparator.compare(key, lastLeftKeyExclusive) < 0) left.remove(key) else right.remove(key)

    override fun putAll(from: Map<out K, V>) = from.forEach(::put)

    override fun put(key: K, value: V): V? =
        if (comparator.compare(key, lastLeftKeyExclusive) < 0) left.put(key, value) else right.put(key, value)

    override fun isEmpty(): Boolean = left.isEmpty() && right.isEmpty()
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = SplitMutableSet(left.entries, right.entries)
    override val keys: MutableSet<K>
        get() = SplitMutableSet(left.keys, right.keys)

    override val size: Int
        get() = left.size + right.size
    override val values: MutableCollection<V>
        get() = SplitMutableCollection(left.values, right.values)
}