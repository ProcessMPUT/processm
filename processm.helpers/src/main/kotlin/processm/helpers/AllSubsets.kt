package processm.helpers

import java.math.BigInteger
import java.util.*

private class Subset<T>(private val base: List<T>, private val mask: Int) :
    kotlin.collections.AbstractCollection<T>(), // avoid loading AbstractSet class, as we override all the methods defined there
    Set<T> {
    // Runs in O(1)
    override val size: Int
        get() = Integer.bitCount(mask)

    // Runs in O(size)
    override fun contains(element: T): Boolean {
        var mask: Int = this.mask
        while (mask != 0) {
            val index = Integer.numberOfTrailingZeros(mask)
            if (base[index] == element)
                return true
            mask = mask and (1 shl index).inv()
        }
        return false
    }

    // Runs in O(1)
    override fun isEmpty(): Boolean = mask == 0

    // The overridden hashCode() is equivalent to the inherited AbstractSet.hashCode() but avoids allocation of an Iterator.
    // Runs in O(size).
    override fun hashCode(): Int {
        var hashCode = 0

        var mask: Int = this.mask
        while (mask != 0) {
            val index = Integer.numberOfTrailingZeros(mask)
            mask = mask and (1 shl index).inv()
            hashCode += base[index]?.hashCode() ?: 0
        }

        return hashCode
    }

    // The overridden equals() is equivalent to the inherited AbstractSet.equals() but avoids allocation of an Iterator.
    // Runs in O(size).
    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is Set<*>) return false

        var mask: Int = this.mask
        if (other is Subset<*> && mask == other.mask && this.base === other.base) return true
        if (this.size != other.size) return false

        while (mask != 0) {
            val index = Integer.numberOfTrailingZeros(mask)
            mask = mask and (1 shl index).inv()
            if (base[index] !in other)
                return false
        }

        return true
    }

    override fun iterator(): Iterator<T> = object : Iterator<T> {
        private var mask: Int = this@Subset.mask
        override fun hasNext(): Boolean = mask != 0

        override fun next(): T {
            val index = Integer.numberOfTrailingZeros(mask)
            mask = mask and (1 shl index).inv()
            return base[index]
        }
    }
}

/**
 * The power set of the set of type [T].
 */
interface PowerSet<T> : List<Set<T>>, Set<Set<T>>, RandomAccess {
    override fun spliterator(): Spliterator<Set<T>>
}

private class PowerSetImpl<T>(val base: List<T>, val offset: Int) :
    kotlin.collections.AbstractList<Set<T>>(),
    PowerSet<T> {

    override fun get(index: Int): Set<T> = Subset(base, index + offset)
    override val size: Int = (1 shl base.size) - offset // overflow is expected for size=31

    // Runs in O(base.size * element.size)
    override fun contains(element: Set<T>): Boolean {
        // The inherited AbstractCollection.contains() runs in O(2^base.size * element.size)
        if (element.size > base.size || element.size < offset) return false
        return base.containsAll(element)
    }

    // Runs in O(base.size * element.size)
    override fun indexOf(element: Set<T>): Int {
        // The inherited AbstractList.indexOf() runs in O(2^base.size * element.size)
        if (element.size > base.size) return -1
        var index: Int = 0
        for (item in element) {
            val i = base.indexOf(item)
            if (i == -1) return -1
            index = index or (1 shl i)
        }
        return index - offset
    }

    // Runs in O(base.size * element.size)
    // All elements are distinct by definition, so lastIndexOf = indexOf
    override fun lastIndexOf(element: Set<T>): Int = indexOf(element)

    // Runs in O(base.size)
    override fun hashCode(): Int {
        // The inherited AbstractList.hashCode() runs in O(2^base.size)

        // Use Long to avoid premature arithmetic overflow and return hash codes consistent with AbstractSet.hashCode()
        // without iterating over the entire power set.
        var hashCode: Long = 0L
        for (element in base)
            hashCode += element?.hashCode()?.toLong() ?: 0L
        return (hashCode * ((size + offset) shr 1)).toInt() // expect overflow here
    }

    // Runs in O(other.size^2 * base.size)
    override fun equals(other: Any?): Boolean {
        // The inherited AbstractList.equals() runs in O(other.size^2 * 2^base.size)
        if (other === this) return true
        if (other !is Collection<*>) return false
        if (other !is Set<*> && other !is List<*>) return false
        if (other is PowerSetImpl<*>) return this.offset == other.offset && this.base == other.base
        if (this.size != other.size) return false
        return this.containsAll(other)
    }

    override fun spliterator(): Spliterator<Set<T>> = super.spliterator()
}

/**
 * Lazily computes the power set view on the given [Collection].
 *
 * This implementation of power set supports collections of the size up to 31 if [excludeEmpty]=true, and 30 otherwise.
 *
 * @param excludeEmpty Controls whether to skip the empty subset. Default: false.
 * @param inline Controls whether to use the given [Collection] as backing memory. If [inline]=true (the default) and
 * the receiver [Collection] is a [RandomAccess] [List], then this function uses the receiver as backing memory, so any
 * change to the receiver invalidates the output of this function. If the receiver is mutable, it is recommended to set
 * [inline]=false. For [inline]=false or a non-[RandomAccess]-[List] the receiver is internally copied.
 * @return The power set of subsets.
 * @throws IllegalArgumentException When the receiver [Collection] is larger than the above-mentioned size limit.
 */
fun <T> Collection<T>.allSubsets(excludeEmpty: Boolean = false, inline: Boolean = true): PowerSet<T> {
    require(excludeEmpty && this.size < Int.SIZE_BITS || this.size < Int.SIZE_BITS - 1) {
        "This implementation of power set supports collections of the size up to ${Int.SIZE_BITS - 1} if excludeEmpty=true, and ${Int.SIZE_BITS - 2} otherwise."
    }

    val list = if (inline && this is List<T> && this is RandomAccess) this else this.toList()
    return PowerSetImpl(list, if (excludeEmpty) 1 else 0)
}

private interface Mask<M> {
    val value: M
    val bitCount: Int
    val numberOfTrailingZeros: Int
    val isZero: Boolean

    /**
     * @return value and (1 shl bit).inv()
     */
    fun erase(bit: Int): Mask<M>
}

private class IntMask(override val value: Int) : Mask<Int> {
    override val bitCount: Int
        get() = Integer.bitCount(value)
    override val numberOfTrailingZeros: Int
        get() = Integer.numberOfTrailingZeros(value)
    override val isZero: Boolean
        get() = value == 0

    override fun erase(bit: Int) = IntMask(value and (1 shl bit).inv())
}

private class LongMask(override val value: Long) : Mask<Long> {
    override val bitCount: Int
        get() = java.lang.Long.bitCount(value)
    override val numberOfTrailingZeros: Int
        get() = java.lang.Long.numberOfTrailingZeros(value)
    override val isZero: Boolean
        get() = value == 0L

    override fun erase(bit: Int) = LongMask(value and (1L shl bit).inv())
}

private class BigIntegerMask(val bitSize: Int, override val value: BigInteger) : Mask<BigInteger> {
    override val bitCount: Int
        get() = value.bitCount()
    override val numberOfTrailingZeros: Int
        get() {
            val n = value.lowestSetBit
            return if (n < 0) bitSize else n
        }
    override val isZero: Boolean
        get() = value == BigInteger.ZERO

    override fun erase(bit: Int) = BigIntegerMask(bitSize, value.clearBit(bit))

}

private class MaskedSubset<T, M>(private val base: List<T>, private val mask: Mask<M>) :
    kotlin.collections.AbstractCollection<T>(), // avoid loading AbstractSet class, as we override all the methods defined there
    Set<T> {
    // Runs in O(1)
    override val size: Int
        get() = mask.bitCount

    // Runs in O(size)
    override fun contains(element: T): Boolean {
        var mask = this.mask
        while (!mask.isZero) {
            val index = mask.numberOfTrailingZeros
            if (base[index] == element)
                return true
            mask = mask.erase(index)
        }
        return false
    }

    // Runs in O(1)
    override fun isEmpty(): Boolean = mask.isZero

    // The overridden hashCode() is equivalent to the inherited AbstractSet.hashCode() but avoids allocation of an Iterator.
    // Runs in O(size).
    override fun hashCode(): Int {
        var hashCode = 0

        var mask = this.mask
        while (!mask.isZero) {
            val index = mask.numberOfTrailingZeros
            mask = mask.erase(index)
            hashCode += base[index]?.hashCode() ?: 0
        }

        return hashCode
    }

    // The overridden equals() is equivalent to the inherited AbstractSet.equals() but avoids allocation of an Iterator.
    // Runs in O(size).
    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is Set<*>) return false

        var mask = this.mask
        if (other is MaskedSubset<*, *> && mask == other.mask && this.base === other.base) return true
        if (this.size != other.size) return false

        while (!mask.isZero) {
            val index = mask.numberOfTrailingZeros
            mask = mask.erase(index)
            if (base[index] !in other)
                return false
        }

        return true
    }

    override fun iterator(): Iterator<T> = object : Iterator<T> {
        private var mask = this@MaskedSubset.mask
        override fun hasNext(): Boolean = !mask.isZero

        override fun next(): T {
            val index = mask.numberOfTrailingZeros
            mask = mask.erase(index)
            return base[index]
        }
    }
}

private class IntLimitedSubset<T>(private val base: List<T>, private val maxSize: Int) : Iterable<Set<T>> {
    override fun iterator(): Iterator<Set<T>> = object : Iterator<Set<T>> {

        var size = 1
        var mask = 1
        var last = 1 shl (base.size - size)

        override fun hasNext(): Boolean = size <= maxSize

        override fun next(): Set<T> {
            val result = MaskedSubset(base, IntMask(mask))
            if (mask == last) {
                size++
                mask = (1 shl size) - 1
                last = mask shl (base.size - size)
            } else {
                // http://graphics.stanford.edu/~seander/bithacks.html#NextBitPermutation
                val t = mask or (mask - 1)
                mask = (t + 1) or (((t.inv() and -t.inv()) - 1) shr (Integer.numberOfTrailingZeros(mask) + 1))
            }
            return result
        }

    }
}

private class LongLimitedSubset<T>(private val base: List<T>, private val maxSize: Int) : Iterable<Set<T>> {
    override fun iterator(): Iterator<Set<T>> = object : Iterator<Set<T>> {

        var size = 1
        var mask = 1L
        var last = 1L shl (base.size - size)

        override fun hasNext(): Boolean = size <= maxSize

        override fun next(): Set<T> {
            val result = MaskedSubset(base, LongMask(mask))
            if (mask == last) {
                size++
                mask = (1L shl size) - 1
                last = mask shl (base.size - size)
            } else {
                // http://graphics.stanford.edu/~seander/bithacks.html#NextBitPermutation
                val t = mask or (mask - 1)
                mask = (t + 1) or (((t.inv() and -t.inv()) - 1) shr (java.lang.Long.numberOfTrailingZeros(mask) + 1))
            }
            return result
        }

    }
}

private class BigIntegerLimitedSubset<T>(private val base: List<T>, private val maxSize: Int) : Iterable<Set<T>> {
    override fun iterator(): Iterator<Set<T>> = object : Iterator<Set<T>> {

        var size = 1
        var mask = BigInteger.ONE
        var last = BigInteger.ONE shl (base.size - size)

        override fun hasNext(): Boolean = size <= maxSize

        override fun next(): Set<T> {
            val ONE = BigInteger.ONE
            val result = MaskedSubset(base, BigIntegerMask(base.size, mask))
            if (mask == last) {
                size++
                mask = (ONE shl size) - ONE
                last = mask shl (base.size - size)
            } else {
                // http://graphics.stanford.edu/~seander/bithacks.html#NextBitPermutation
                val t = mask or (mask - ONE)
                var numOfTrailinigZeros = mask.lowestSetBit
                if (numOfTrailinigZeros < 0)
                    numOfTrailinigZeros = base.size
                mask = (t + ONE) or (((t.inv() and -t.inv()) - ONE) shr (numOfTrailinigZeros + 1))
            }
            return result
        }

    }
}

fun <T> List<T>.allSubsetsUpToSize(maxSize: Int): Iterable<Set<T>> {
    if (maxSize >= this.size)
        return this.allSubsets(true)
    else {
        require(maxSize < Int.MAX_VALUE)
        if (size < Int.SIZE_BITS)
            return IntLimitedSubset(this, maxSize)
        else if (size < Long.SIZE_BITS)
            return LongLimitedSubset(this, maxSize)
        else
            return BigIntegerLimitedSubset(this, maxSize)
    }
}

/**
 * Eagerly computes the power set view on the given [Collection]. The empty set is excluded if [filterOutEmpty] is true.
 *
 * This function seems to be more efficient if one knows that the whole powerset is going to be used.
 * Otherwise, [allSubsets] should be the preferred solution, as it does not perform eager materialization.
 */
@Deprecated("This function was inefficient", ReplaceWith("allSubsets(filterOutEmpty)"), level = DeprecationLevel.ERROR)
fun <T> Collection<T>.materializedAllSubsets(filterOutEmpty: Boolean): PowerSet<T> =
    this.allSubsets(filterOutEmpty)
