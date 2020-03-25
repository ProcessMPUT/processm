package processm.core.helpers

import processm.core.log.attribute.deepEquals
import processm.core.log.hierarchical.Log
import processm.core.logging.logger
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
        false
    }

infix fun <T, R> Sequence<T>.zipOrThrow(seq2: Sequence<R>): Sequence<Pair<T, R>> = sequence {
    val it1: Iterator<T> = this@zipOrThrow.iterator()
    val it2: Iterator<R> = seq2.iterator()
    while (it1.hasNext() && it2.hasNext()) {
        val a = it1.next()
        val b = it2.next()
        yield(a to b)
    }
    if (it1.hasNext() || it2.hasNext())
        throw IllegalArgumentException("Inconsistent sizes of the given sequences")
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
 * Generate all permutations of the given list
 */
fun <T> List<T>.allPermutations(): List<List<T>> {
    val first = this.first()
    val rest = this.drop(1)
    if (rest.isNotEmpty()) {
        return rest.allPermutations()
            .flatMap { perm ->
                perm.indices.map { perm.subList(0, it) + first + perm.subList(it, perm.size) } +
                        listOf(perm + first)
            }
    } else {
        return listOf(listOf(first))
    }
}