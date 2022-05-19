package processm.core.helpers

import processm.core.log.attribute.deepEquals
import processm.core.log.hierarchical.Log
import processm.core.logging.logger
import java.math.BigInteger
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

private const val EnvironmentVariablePrefix = "processm_"
private var configurationLoaded = AtomicBoolean(false)

private object Helpers {
    val logger = logger()
}


/**
 * Load system configuration from environment variables and config.properties (in this order) into
 * system properties. Any variable found in the successive source overwrites its value from the
 * preceding source.
 *
 * This function is thread-safe given [overwriteIfAlreadyLoaded]==false and not thread-safe otherwise.
 *
 * @param overwriteIfAlreadyLoaded Should we force reload configuration? This parameter is for test-use
 * only. false is default.
 * @see System.getProperties
 */
fun loadConfiguration(overwriteIfAlreadyLoaded: Boolean = false) {
    // configurationLoaded.compareAndSet(false, true) returns true for the first run
    if (!configurationLoaded.compareAndSet(false, true) && !overwriteIfAlreadyLoaded)
        return
    assert(configurationLoaded.get())

    // Load from environment variables processm_* by replacing _ with .
    System.getenv().filterKeys { it.startsWith(EnvironmentVariablePrefix, true) }.forEach {
        val key = it.key.replace("_", ".")
        Helpers.logger.debug("Setting parameter $key=${it.value} using environment variable ${it.key}")
        System.setProperty(key, it.value)
    }

    // Load from configuration file, possibly overriding the environment settings
    Helpers::class.java.classLoader.getResourceAsStream("config.properties").use {
        Properties().apply { load(it) }.forEach {
            Helpers.logger.debug("Setting parameter ${it.key}=${it.value} using config.properties")
            System.setProperty(it.key as String, it.value as String)
        }
    }
}

/**
 * Reads the system property ignoring the key case.
 *
 * @see [System.getProperty]
 */
fun getPropertyIgnoreCase(key: String): String? =
    System.getProperty(key)
        ?: System.getProperties().entries.firstOrNull { (k, _) -> (k as String).equals(key, true) }?.value as String?

/**
 * Compares two logs.
 * @return True if the logs equal, false otherwise.
 */
fun hierarchicalCompare(seq1: Sequence<Log>, seq2: Sequence<Log>): Boolean =
    try {
        (seq1 zipOrThrow seq2).all { (l1, l2) ->
            l1 == l2 && l1.attributesInternal.deepEquals(l2.attributesInternal)
                    && l1.traceGlobalsInternal.deepEquals(l2.traceGlobalsInternal)
                    && l1.eventGlobalsInternal.deepEquals(l2.eventGlobalsInternal)
                    && (l1.traces zipOrThrow l2.traces).all { (t1, t2) ->
                t1 == t2 && t1.attributesInternal.deepEquals(t2.attributesInternal)
                        && (t1.events zipOrThrow t2.events).all { (e1, e2) ->
                    e1 == e2 && e1.attributesInternal.deepEquals(e2.attributesInternal)
                }
            }
        }
    } catch (e: IllegalArgumentException) {
        Helpers.logger.debug(e.message)
        false
    }

/**
 * Combines two sequences element-wise into pairs. Differs from [zip] in that it throws [IllegalArgumentException] if
 * the length of the sequences differ.
 *
 * @receiver The left-hand part sequence.
 * @param seq2 The right-hand part sequence.
 * @return A sequence of pairs of the corresponding elements from the given sequences.
 * @throws IllegalArgumentException If the lengths of the sequences differ.
 */
infix fun <T, R> Sequence<T>.zipOrThrow(seq2: Sequence<R>): Sequence<Pair<T, R>> = sequence {
    val it1: Iterator<T> = this@zipOrThrow.iterator()
    val it2: Iterator<R> = seq2.iterator()
    while (it1.hasNext() && it2.hasNext()) {
        val a = it1.next()
        val b = it2.next()
        yield(a to b)
    }
    if (it1.hasNext() || it2.hasNext())
        throw IllegalArgumentException("Inconsistent sizes of the given sequences.")
}

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
@Deprecated("This function was inefficient", ReplaceWith("allSubsets(filterOutEmpty)"))
fun <T> Collection<T>.materializedAllSubsets(filterOutEmpty: Boolean): PowerSet<T> =
    this.allSubsets(filterOutEmpty)

/**
 * Generate all permutations of the given list
 */

fun <T> Collection<T>.allPermutations(): Sequence<ArrayList<T>> = sequence {
    if (this@allPermutations.isEmpty())
        return@sequence

    yield(ArrayList(this@allPermutations))

    val A = this@allPermutations.toMutableList()
    val n = A.size
    val c = IntArray(n)

    var i = 0
    while (i < n) {
        if (c[i] < i) {
            Collections.swap(A, (i and 1) * c[i], i)
            yield(ArrayList(A))
            ++c[i]
            i = 0
        } else {
            c[i] = 0
            ++i
        }
    }
}

private class PairedCollection<T>(val backingCollection: List<T>) : Collection<Pair<T, T>> {
    override val size: Int
        get() = backingCollection.size * (backingCollection.size - 1) / 2

    override fun contains(pair: Pair<T, T>): Boolean =
        pair.first in backingCollection && pair.second in backingCollection

    override fun containsAll(pairs: Collection<Pair<T, T>>): Boolean {
        for (pair in pairs) {
            if (pair !in this)
                return false
        }
        return true
    }

    override fun isEmpty(): Boolean = backingCollection.size <= 1

    override fun iterator(): Iterator<Pair<T, T>> = iterator {
        for ((i, e) in backingCollection.withIndex()) {
            val iterator = backingCollection.listIterator(i + 1)
            while (iterator.hasNext())
                yield(Pair(e, iterator.next()))
        }
    }
}

/**
 * Lazily calculates all pairs of the items in this list. The returned collection is view on this list
 * and all changes to this list are immediately reflected in the returned collection.
 */
fun <T> List<T>.allPairs(): Collection<Pair<T, T>> =
    PairedCollection(this)

/**
 * Returns index of the first element matching the given [predicate] beginning from [startIndex], or -1 if the list does
 * not contain such element.
 */
inline fun <T> List<T>.indexOfFirst(startIndex: Int, predicate: (item: T) -> Boolean): Int {
    val iterator = this.listIterator(startIndex.coerceAtLeast(0))
    while (iterator.hasNext()) {
        if (predicate(iterator.next()))
            return iterator.previousIndex()
    }

    return -1
}

/**
 * Parses a timestamp with timezone in the ISO-8601 format into [Instant].
 */
inline fun String.parseISO8601(): Instant = DateTimeFormatter.ISO_DATE_TIME.parse(this, Instant::from)

/**
 * Parses a timestamp with timezone in the ISO-8601 format into [Instant].
 * This function trades some safety-checks for performance. E.g.,
 * * The exception messages may be less detailed than these thrown by [parseISO8601] but the normal results of both
 * methods should equal.
 */
inline fun String.fastParseISO8601(): Instant =
    DateTimeFormatter.ISO_DATE_TIME.parse(this) { temporal ->
        val instantSecs = temporal.getLong(ChronoField.INSTANT_SECONDS)
        val nanoOfSecond = temporal.get(ChronoField.NANO_OF_SECOND).toLong()
        Instant.ofEpochSecond(instantSecs, nanoOfSecond)
    }

inline fun Instant.toDateTime(): OffsetDateTime = this.atOffset(ZoneOffset.UTC)

/**
 * Converts an [Instant] to [LocalDateTime] in a uniform way.
 */
fun Instant.toLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(this, ZoneId.of("Z"))

/**
 * Returns a set containing the results of applying the given [transform] function
 * to each element in the original collection.
 */
inline fun <T, R> Iterable<T>.mapToSet(transform: (T) -> R): Set<R> = mapTo(HashSet<R>(), transform)

/**
 * Returns a set containing the results of applying the given [transform] function
 * to each element in the original sequence.
 */
inline fun <T, R> Sequence<T>.mapToSet(transform: (T) -> R): Set<R> = mapTo(HashSet<R>(), transform)

/**
 * Returns an [Array] containing the results of applying the given [transform] function
 * to each element in the original [Collection].
 */
inline fun <T, reified R> Collection<T>.mapToArray(transform: (T) -> R): Array<R> = this.iterator().let {
    Array<R>(this.size) { _ -> transform(it.next()) }
}

/**
 * Returns an [Array] containing the results of applying the given [transform] function to each element in the original
 * [Sequence].
 */
inline fun <T, reified R> Sequence<T>.mapToArray(transform: (T) -> R): Array<R> = ArrayList<R>().apply {
    for (item in this@mapToArray)
        add(transform(item))
}.toTypedArray()

/**
 * Returns an [Array] containing the results of applying the given [transform] function
 * to each element in the original [Array].
 */
inline fun <T, reified R> Array<T>.mapToArray(transform: (T) -> R): Array<R> = this.iterator().let {
    Array<R>(this.size) { _ -> transform(it.next()) }
}

/**
 * Retuns a map whose keys refer to the values of the given map and values refer to the keys of the given map.
 * @throws IllegalArgumentException If the mapping of the given map is non-injective.
 */
inline fun <K, V> Map<K, V>.inverse(): Map<V, K> = HashMap<V, K>().also {
    for ((key, value) in this)
        require(it.put(value, key) == null) { "The given mapping is non-injective." }
}

inline fun <E, T : Collection<E>> T?.ifNullOrEmpty(default: () -> T): T =
    if (this.isNullOrEmpty())
        default()
    else
        this

/**
 * Returns the smallest value among all values produced by [selector] function
 * applied to each element in the collection.
 *
 * @throws NoSuchElementException if the collection is empty.
 */
inline fun <T, R : Comparable<R>> Iterator<T>.minOf(selector: (T) -> R): R {
    if (!hasNext()) throw NoSuchElementException()
    var minValue = selector(next())
    while (hasNext()) {
        val v = selector(next())
        if (minValue > v) {
            minValue = v
        }
    }
    return minValue
}

/**
 * Replaces this [List] with either immutable singleton empty list or immutable single-item [List] if this [List] is empty
 * or contains just one element, respectively. Otherwise, returns this [List].
 * The main purpose of this function is to reduce memory footprint of storing small immutable collections backed by
 * varying-size mutable collections.
 */
fun <T> List<T>.optimize(): List<T> = when (this.size) {
    0 -> emptyList()
    1 -> Collections.singletonList(this[0])
    else -> {
        if (this is ArrayList<T>)
            trimToSize()
        this
    }
}

/**
 * Replaces this [Set] with either immutable singleton empty set or immutable single-item [Set] if this [Set] is empty
 * or contains just one element, respectively. Otherwise, returns this [Set].
 * The main purpose of this function is to reduce memory footprint of storing small immutable collections backed by
 * varying-size mutable collections.
 */
fun <T> Set<T>.optimize(): Set<T> = when (this.size) {
    0 -> emptySet()
    1 -> Collections.singleton(this.first())
    else -> this
}

/**
 * Replaces this [Map] with either immutable singleton empty map or immutable single-item [Map] if this [Map] is empty
 * or contains just one element, respectively. Otherwise, returns this [Map].
 * The main purpose of this function is to reduce memory footprint of storing small immutable collections backed by
 * varying-size mutable collections.
 */
fun <K, V> Map<K, V>.optimize(): Map<K, V> = when (this.size) {
    0 -> emptyMap()
    1 -> Collections.singletonMap(this.keys.first(), this.values.first())
    else -> this
}


/**
 * Casts [IntProgression] to an equivalent [LongRange].
 */
inline fun IntProgression.toLongRange(): LongRange = this.first.toLong()..this.last.toLong()

/**
 * Material conditional.
 *
 * @see [https://en.wikipedia.org/wiki/Material_conditional]
 */
inline infix fun Boolean.implies(consequence: Boolean) = !this || consequence

/**
 * Material conditional.
 *
 * This override of the [implies] function evaluates [consequence] only of the receiver condition evaluates to true.
 * @see [https://en.wikipedia.org/wiki/Material_conditional]
 */
inline infix fun (() -> Boolean).implies(consequence: () -> Boolean) = !(this() && !consequence())

/**
 * Like [forEach] but catches exceptions thrown by successive invocations of [action] and rethrows the first encountered
 * exception with the successive exceptions suppressed (if any).
 */
inline fun <T> Iterable<T>.forEachCatching(action: (T) -> Unit) {
    var exception: Throwable? = null
    for (item in this) {
        try {
            action(item)
        } catch (e: Throwable) {
            if (exception === null)
                exception = e
            else
                exception.addSuppressed(e)
        }
    }
    if (exception !== null)
        throw exception
}

/**
 * Like [forEach] but catches exceptions thrown by successive invocations of [action] and rethrows the first encountered
 * exception with the successive exceptions suppressed (if any).
 */
inline fun <T> Array<T>.forEachCatching(action: (T) -> Unit) =
    Arrays.asList(*this).forEachCatching(action)


/**
 * Returns the set of elements shared by all the sets in [sets]
 */
fun <T> intersect(sets: Collection<Set<T>>): Set<T> =
    when (sets.size) {
        0 -> emptySet()
        1 -> sets.single()
        else -> {
            val i = sets.iterator()
            val result = HashSet<T>(i.next())
            while (i.hasNext() && result.isNotEmpty())
                result.retainAll(i.next())
            result
        }
    }