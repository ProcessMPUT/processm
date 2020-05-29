package processm.core.helpers

import processm.core.log.attribute.deepEquals
import processm.core.log.hierarchical.Log
import processm.core.logging.logger
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

private const val EnvironmentVariablePrefix = "processm_"
private var configurationLoaded = AtomicBoolean(false)

private object Helpers {
    internal val logger = logger()
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

private class Subset<T>(private val base: List<T>, private val mask: Int) : Collection<T> {
    companion object {
        val empty: Subset<Nothing> = Subset(emptyList(), 0)
    }

    override val size: Int
        get() = Integer.bitCount(mask)

    /**
     * Runs in O([size]).
     */
    override fun contains(element: T): Boolean = this.any { it == element }

    /**
     * Runs in O([size] * [elements.size]).
     */
    override fun containsAll(elements: Collection<T>): Boolean = elements.all { contains(it) }

    /**
     * Runs in O(1).
     */
    override fun isEmpty(): Boolean = mask == 0


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

private class LongSubset<T>(private val base: List<T>, private val mask: Long) : Collection<T> {
    companion object {
        val empty: Subset<Nothing> = Subset(emptyList(), 0)
    }

    override val size: Int
        get() = java.lang.Long.bitCount(mask)

    /**
     * Runs in O([size]).
     */
    override fun contains(element: T): Boolean = this.any { it == element }

    /**
     * Runs in O([size] * [elements.size]).
     */
    override fun containsAll(elements: Collection<T>): Boolean = elements.all { contains(it) }

    /**
     * Runs in O(1).
     */
    override fun isEmpty(): Boolean = mask == 0L


    override fun iterator(): Iterator<T> = object : Iterator<T> {
        private var mask: Long = this@LongSubset.mask
        override fun hasNext(): Boolean = mask != 0L

        override fun next(): T {
            val index = java.lang.Long.numberOfTrailingZeros(mask)
            mask = mask and (1L shl index).inv()
            return base[index]
        }
    }
}

/**
 * Lazily computes the power set view on the given [List]. The empty set is excluded if [filterOutEmpty] is true.
 *
 * This function uses the receiver [List] as backing memory, so any change to that [List] invalidates the output
 * of this function. If the receiver [List] is mutable, it is recommended to copy that [List] before calling this
 * function. E.g.,
 * ```
 * receiver.toList().aLlSubsets(false)
 * ```
 */
fun <T> List<T>.allSubsets(filterOutEmpty: Boolean = false): Sequence<Collection<T>> = sequence {
    require(this@allSubsets.size < Long.SIZE_BITS) { "This implementation of power set supports sets of up to 63 items." }
    if (this@allSubsets.isEmpty()) {
        if (!filterOutEmpty)
            yield(emptyList<T>())
        return@sequence
    }

    val lastBucketMask: Long = -1L ushr (Long.SIZE_BITS - this@allSubsets.size)

    var mask = if (filterOutEmpty) 1L else 0L
    while (true) {
        yield(LongSubset(this@allSubsets, mask))

        if (++mask > lastBucketMask || mask < 0L)
            return@sequence
    }
}

/**
 * Lazily computes the power set view on the given [List]. The empty set is excluded if [filterOutEmpty] is true.
 */
fun <T> Collection<T>.allSubsets(filterOutEmpty: Boolean = false): Sequence<Collection<T>> {
    assert(this !is List<T>)
    return this.toList().allSubsets(filterOutEmpty)
}

/**
 * Eagerly computes the power set view on the given [List]. The empty set is excluded if [filterOutEmpty] is true.
 *
 * This function uses the receiver [List] as backing memory, so any change to that [List] invalidates the output
 * of this function. If the receiver [List] is mutable, it is recommended to copy that [List] before calling this
 * function. E.g.,
 * ```
 * receiver.toList().materializedALlSubsets(false)
 * ```
 *
 * This function seems to be more efficient if one knows that the whole powerset is going to be used.
 * Otherwise, [allSubsets] should be the preferred solution, as it does not perform eager materialization.
 */
fun <T> List<T>.materializedAllSubsets(filterOutEmpty: Boolean): List<Collection<T>> {
    // The collection of size 31 cannot be handled this way, as Int is unable to hold 2^31 and the resulting ArrayList cannot be created
    require(this.size <= 30) { "This implementation of power set supports collections of up to 30 items." }
    if (this.isEmpty()) {
        return if (filterOutEmpty) emptyList() else listOf(Subset.empty as Collection<T>)
    }

    val lastBucketMask: Int = -1 ushr (Int.SIZE_BITS - this.size)
    var mask: Int = if (filterOutEmpty) 1 else 0

    if (this.size >= 18) {
        Helpers.logger.warn("Attempted to materialize the power set of the collection of size ${this.size}. Expect high memory pressure.")
        if (this.size >= 26) // good luck
            System.gc()
        /*
        The below table shows the expected memory usage if the compressed oops are on. These values roughly double for
        disabled compressed oops. The memory usage is calculated as
        2^size * (4 /*reference to a Subset<T>*/ + 12 /*internals of the Subset<T>*/)
        | size | memory usage |
        | 16   |          1MB |
        | 18   |          4MB |
        | 20   |         16MB |
        | 22   |         64MB |
        | 24   |        256MB |
        | 26   |          1GB |
        | 28   |          4GB |
        | 30   |         16GB |
        */
    }

    val result = ArrayList<Subset<T>>((1 shl this.size) - mask)
    while (true) {
        result.add(Subset(this, mask))

        assert((mask + 1) >= 0) { "Overflow should not happen here." }
        if (++mask > lastBucketMask /*|| mask < 0*/) {
            assert(result.size == (1 shl this.size) - (if (filterOutEmpty) 1 else 0))
            return result
        }
    }
}


/**
 * Eagerly computes the power set view on the given [Collection]. The empty set is excluded if [filterOutEmpty] is true.
 *
 * This function seems to be more efficient if one knows that the whole powerset is going to be used.
 * Otherwise, [allSubsets] should be the preferred solution, as it does not perform eager materialization.
 */
fun <T> Collection<T>.materializedAllSubsets(filterOutEmpty: Boolean): List<Collection<T>> {
    assert(this !is List<T>)
    return this.toList().materializedAllSubsets(filterOutEmpty)
}

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
            A.swap((i and 1) * c[i], i)
            yield(ArrayList(A))
            ++c[i]
            i = 0
        } else {
            c[i] = 0
            ++i
        }
    }
}

private inline fun <T> MutableList<T>.swap(i: Int, j: Int) {
    val tmp = this[i]
    this[i] = this[j]
    this[j] = tmp
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

inline fun <E, T : Collection<E>> T?.ifNullOrEmpty(default: () -> T): T =
    if (this.isNullOrEmpty())
        default()
    else
        this
