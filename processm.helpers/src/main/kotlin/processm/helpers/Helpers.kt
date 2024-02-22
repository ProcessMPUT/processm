package processm.helpers

import org.jetbrains.exposed.sql.SizedIterable
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.util.*

/**
 * Reads the system property ignoring the key case.
 *
 * @see [System.getProperty]
 */
fun getPropertyIgnoreCase(key: String): String? =
    System.getProperty(key)
        ?: System.getProperties().entries.firstOrNull { (k, _) -> (k as String).equals(key, true) }?.value as String?

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
 * @throws java.time.format.DateTimeParseException if unable to parse the requested string
 */
inline fun String.parseISO8601(): Instant = DateTimeFormatter.ISO_DATE_TIME.parse(this, Instant::from)

/**
 * Parses a timestamp with timezone in the ISO-8601 format into [Instant].
 * This function trades some safety-checks for performance. E.g.,
 * * The exception messages may be less detailed than these thrown by [parseISO8601] but the normal results of both
 * methods should equal.
 * @throws java.time.format.DateTimeParseException if unable to parse the requested string
 * @throws java.time.DateTimeException if the date/time cannot be represented using [Instant]
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
 * Returns an [Array] containing the results of applying the given [transform] function
 * to each element in the original [SizedIterable].
 */
inline fun <T, reified R> SizedIterable<T>.mapToArray(transform: (T) -> R): Array<R> = this.iterator().let {
    Array<R>(this.count().toInt()) { _ -> transform(it.next()) }
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

/**
 * Returns the sum of all elements in this collection as [Long].
 */
fun Iterable<Int>.longSum(): Long {
    var sum = 0L
    for (element in this) {
        sum += element
    }
    return sum
}
