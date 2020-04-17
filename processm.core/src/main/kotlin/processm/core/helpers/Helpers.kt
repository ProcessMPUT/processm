package processm.core.helpers

import processm.core.log.attribute.deepEquals
import processm.core.log.hierarchical.Log
import processm.core.logging.logger
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

private const val EnvironmentVariablePrefix = "processm_"
private var configurationLoaded = AtomicBoolean(false)

private object Helpers


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
        Helpers.logger().debug("Setting parameter $key=${it.value} using environment variable ${it.key}")
        System.setProperty(key, it.value)
    }

    // Load from configuration file, possibly overriding the environment settings
    Helpers::class.java.classLoader.getResourceAsStream("config.properties").use {
        Properties().apply { load(it) }.forEach {
            Helpers.logger().debug("Setting parameter ${it.key}=${it.value} using config.properties")
            System.setProperty(it.key as String, it.value as String)
        }
    }
}

/**
 * Compares two logs.
 * @return True of the logs equal, false otherwise.
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
        Helpers.logger().debug(e.message)
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

/**
 * Generates the power-set of the collection (incl. the empty set and the full set)
 */
fun <T> Collection<T>.allSubsets(): Sequence<List<T>> = sequence {
    if (this@allSubsets.size >= Long.SIZE_BITS)
        throw IllegalArgumentException("This implementation of power set supports sets of up to 63 items.")
    if (this@allSubsets.isEmpty()) {
        yield(listOf<T>())
        return@sequence
    }

    val lastBucketMask: Long = -1L ushr (Long.SIZE_BITS - this@allSubsets.size)

    var mask = 0L
    while (true) {
        yield(this@allSubsets.filterIndexed { index, _ -> (mask and (1L shl index)) != 0L })

        if (++mask > lastBucketMask || mask < 0L)
            return@sequence
    }
}

/**
 * Eagerly computes powerset. The empty set is included if [filterEmpty] is true.
 *
 * This seems to be more efficient if one knows that the whole powerset is going to be used.
 * Otherwise, [allSubsets] should be the preferred solution, as it does not perform eager materialization.
 */
fun <T> Collection<T>.materializedAllSubsets(filterEmpty: Boolean): List<List<T>> {
    require(this.size < Int.SIZE_BITS) { "This implementation of power set supports sets of up to 31 items." }
    if (this.isEmpty()) {
        return if (filterEmpty)
            emptyList()
        else
            listOf(emptyList())
    }

    val lastBucketMask: Long = -1L ushr (Long.SIZE_BITS - this.size)

    var mask = 0L
    val result = ArrayList<List<T>>(1 shl (this.size + 1))
    while (true) {
        val tmp = this.filterIndexed { index, _ -> (mask and (1L shl index)) != 0L }
        if (!filterEmpty || tmp.isNotEmpty())
            result.add(tmp)

        if (++mask > lastBucketMask || mask < 0L)
            return result
    }
}

/**
 * Generate all permutations of the given list
 */
fun <T> Collection<T>.allPermutations(): Sequence<ArrayList<T>> = sequence {
    if (this@allPermutations.isEmpty())
        return@sequence

    yield(ArrayList(this@allPermutations))

    var A = this@allPermutations.toMutableList()
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
 * Parses a timestamp with timezone in ISO-8601 format into [Instant].
 */
inline fun String.parseISO8601(): Instant = DateTimeFormatter.ISO_DATE_TIME.parse(this, Instant::from)