package processm.helpers

import com.carrotsearch.hppc.ObjectByteHashMap

interface MutableMultiSet<E> : MutableSet<E> {
    /**
     * The total number of unique elements.
     */
    val uniqueSize: Int

    /**
     * Adds [count] copies of [element].
     * @return true if at least one copy is added.
     */
    fun add(element: E, count: Byte): Boolean

    /**
     * Adds all copies of elements from [other].
     */
    fun addAll(other: MutableMultiSet<E>): Boolean

    /**
     * The sequence over elements with associated counts in this multiset.
     * Note that the resulting [Bucket]s are reused in successive iterations.
     */

    fun entrySet(): Sequence<Bucket<E>>

    /**
     * The sequence over unique elements in this multiset.
     */
    fun uniqueSet(): Sequence<E>

    /**
     * The sequence of counts in this multiset.
     */
    fun countSet(): Sequence<Byte>

    /**
     * Removes at most [count] copies of [element].
     * @return true if at least one copy is removed.
     */
    fun remove(element: E, count: Byte): Boolean

    data class Bucket<E>(var element: E, var count: Byte) {
        @Suppress("UNCHECKED_CAST")
        constructor() : this(Unit as E, 0)
    }
}

/**
 * A hash-based multiset that may hold up to [Byte.MAX_VALUE] duplicates of items.
 */
open class HashMultiSet<E>() : MutableMultiSet<E> {

    constructor(other: MutableMultiSet<E>) : this() {
        addAll(other)
    }

    private val backend = ObjectByteHashMap<E>()

    /**
     * The total number of items in this multiset.
     */
    private var cardinality: Int = 0

    /**
     * The total number of elements in this multiset, including duplicates.
     * @see [uniqueSize]
     */
    override val size: Int
        get() {
            assert(cardinality == backend.values().sumOf { it.value.toInt() }) {
                "cardinality: $cardinality values().sumOf { it.value }: ${backend.values().sumOf { it.value.toInt() }}"
            }
            return cardinality
        }

    override fun clear() {
        cardinality = 0
        backend.clear()
    }

    override fun isEmpty(): Boolean {
        assert(backend.isEmpty() == (cardinality == 0))
        return cardinality == 0
    }

    override val uniqueSize: Int
        get() = backend.size()

    override fun add(element: E): Boolean = add(element, 1)

    override fun add(element: E, count: Byte): Boolean {
        assert(count >= 0)
        assert(backend[element] + count <= Byte.MAX_VALUE)

        cardinality += count
        backend.addTo(element, count)
        return count > 0
    }

    override fun addAll(elements: Collection<E>): Boolean {
        for (item in elements)
            add(item)
        return elements.isNotEmpty()
    }

    override fun addAll(other: MutableMultiSet<E>): Boolean {
        for (bucket in other.entrySet())
            add(bucket.element, bucket.count)
        return other.isNotEmpty()
    }

    override fun containsAll(elements: Collection<E>): Boolean =
        this.uniqueSize >= elements.size && elements.all { contains(it) }

    override fun contains(element: E): Boolean = backend.containsKey(element)

    @Deprecated(
        "Use entrySet() or uniqueSet() instead; The current implementation is equivalent to uniqueSet()",
        replaceWith = ReplaceWith("uniqueSet()"),
        level = DeprecationLevel.ERROR
    )
    override fun iterator(): MutableIterator<E> = object : MutableIterator<E>, Iterator<E> by uniqueSet().iterator() {
        override fun remove() = throw UnsupportedOperationException()
    }

    override fun entrySet(): Sequence<MutableMultiSet.Bucket<E>> = sequence<MutableMultiSet.Bucket<E>> {
        val bucket = MutableMultiSet.Bucket<E>()
        for (entry in backend) {
            bucket.element = entry.key
            bucket.count = entry.value
            yield(bucket)
        }
    }

    override fun uniqueSet(): Sequence<E> = sequence<E> {
        for (key in backend.keys())
            yield(key.value)
    }

    override fun countSet(): Sequence<Byte> = sequence {
        for (count in backend.values())
            yield(count.value)
    }

    override fun retainAll(elements: Collection<E>): Boolean =
        backend.retainAll {
            val res = it.key in elements
            if (!res)
                cardinality -= it.value
            res
        }

    override fun remove(element: E): Boolean = remove(element, 1)

    override fun remove(element: E, count: Byte): Boolean {
        val index = backend.indexOf(element)
        if (index < 0)
            return false

        val currentCount = backend.indexGet(index)
        val newCount = (currentCount - count).coerceAtLeast(0)
        if (newCount > 0)
            backend.indexReplace(index, newCount.toByte())
        else
            backend.indexRemove(index)

        val diff = newCount - currentCount
        assert(diff <= 0)
        cardinality += diff

        return diff > 0
    }

    /**
     * Removes one copy of each give item.
     */
    override fun removeAll(elements: Collection<E>): Boolean {
        var removed = false
        for (item in elements)
            removed = remove(item) || removed
        return removed
    }
}
