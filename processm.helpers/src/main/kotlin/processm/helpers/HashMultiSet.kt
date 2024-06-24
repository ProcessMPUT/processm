package processm.helpers

import com.carrotsearch.hppc.ObjectByteHashMap
import processm.helpers.MutableMultiSet.Bucket

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
     * The set of buckets wrapping unique elements and the associated counts in this multiset.
     * Note that [Bucket] returned in the successive iterations of the iterator returned by [Set.iterator] may be reused
     * in the successive iterations.
     */
    fun entrySet(): Set<Bucket<E>>

    /**
     * The set of unique elements in this multiset.
     */
    fun uniqueSet(): Set<E>

    /**
     * The collection of counts corresponding to unique elements in this multiset.
     */
    fun countSet(): Collection<Byte>

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
    private val keysContainer = backend.keys()
    private val valuesContainer = backend.values()

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

    override fun containsAll(elements: Collection<E>): Boolean = when (elements) {
        is MutableMultiSet<E> -> this.uniqueSize >= elements.uniqueSize && elements.uniqueSet().all { contains(it) }
        is Set<E> -> this.uniqueSize >= elements.size && elements.all { contains(it) }
        else -> elements.all { contains(it) }
    }

    override fun contains(element: E): Boolean = backend.containsKey(element)

    @Deprecated(
        "Use entrySet() or uniqueSet() instead; The current implementation is equivalent to uniqueSet()",
        replaceWith = ReplaceWith("uniqueSet()"),
        level = DeprecationLevel.ERROR
    )
    override fun iterator(): MutableIterator<E> = object : MutableIterator<E>, Iterator<E> by uniqueSet().iterator() {
        override fun remove() = throw UnsupportedOperationException()
    }

    /**
     * The set of buckets wrapping unique elements and the associated counts in this multiset.
     * Note that the resulting set is a lightweight view on the underlying collection.
     * Note that the iterator returned by [Set.iterator] is shared by all copies of the set returned by this method.
     * To create independent iterators, create independent views by calling this method twice.
     * Note that [Bucket] returned in the successive iterations of the iterator returned by [Set.iterator] is reused
     * in the successive iterations.
     */
    override fun entrySet(): Set<Bucket<E>> =
        object : Set<Bucket<E>>, Iterator<Bucket<E>> {
            private val base = keysContainer.iterator()
            private val bucket = Bucket<E>()
            override fun hasNext(): Boolean = base.hasNext()
            override fun next(): Bucket<E> {
                val cursor = base.next()
                bucket.element = cursor.value
                bucket.count = backend.values[cursor.index]
                return bucket
            }

            override val size: Int
                get() = uniqueSize

            override fun isEmpty(): Boolean = uniqueSize == 0
            override fun iterator(): Iterator<Bucket<E>> = this
            override fun containsAll(elements: Collection<Bucket<E>>): Boolean =
                throw UnsupportedOperationException()

            override fun contains(element: Bucket<E>): Boolean = throw UnsupportedOperationException()
        }

    /**
     * The set of unique elements in this multiset.
     * Note that the resulting set is a lightweight view on the underlying collection.
     * Note that the iterator returned by [Set.iterator] is shared by all copies of the set returned by this method.
     * To create independent iterators, create independent views by calling this method twice.
     */
    override fun uniqueSet(): Set<E> = object : Set<E>, Iterator<E> {
        private val base = keysContainer.iterator()
        override fun hasNext(): Boolean = base.hasNext()
        override fun next(): E = base.next().value
        override val size: Int
            get() = uniqueSize

        override fun isEmpty(): Boolean = uniqueSize == 0
        override fun iterator(): Iterator<E> = this
        override fun containsAll(elements: Collection<E>): Boolean = elements.all { keysContainer.contains(it) }
        override fun contains(element: E): Boolean = keysContainer.contains(element)
    }

    /**
     * The collection of counts corresponding to unique elements in this multiset.
     * Note that the resulting collection is a lightweight view on the underlying collection.
     * Note that the iterator returned by [Collection.iterator] is shared by all copies of the collection returned by this method.
     * To create independent iterators, create independent views by calling this method twice.
     */
    override fun countSet(): Collection<Byte> = object : Collection<Byte>, ByteIterator() {
        private val base = valuesContainer.iterator()
        override fun hasNext(): Boolean = base.hasNext()
        override fun nextByte(): Byte = base.next().value
        override val size: Int
            get() = valuesContainer.size()

        override fun isEmpty(): Boolean = valuesContainer.isEmpty
        override fun iterator(): ByteIterator = this
        override fun containsAll(elements: Collection<Byte>): Boolean = elements.all { valuesContainer.contains(it) }
        override fun contains(element: Byte): Boolean = valuesContainer.contains(element)
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

    override fun hashCode(): Int = backend.hashCode()

    override fun equals(other: Any?): Boolean {
        if (other !is HashMultiSet<*>) return false
        return backend == other.backend
    }
}
