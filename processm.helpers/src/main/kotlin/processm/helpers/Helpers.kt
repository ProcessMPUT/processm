package processm.helpers

import org.jetbrains.exposed.sql.SizedIterable
import java.util.*
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

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
 * Returns index of the first element matching the given [predicate] beginning from [startIndex] (inclusive), or -1 if
 * the list does not contain such element.
 * @param startIndex The index of the first item to verify the [predicate] for.
 * @throws IndexOutOfBoundsException If [startIndex] is out of bounds of [this] list.
 */
@OptIn(ExperimentalContracts::class)
inline fun <T> List<T>.indexOfFirst(startIndex: Int, predicate: (item: T) -> Boolean): Int {
    contract {
        callsInPlace(predicate)
    }

    if (startIndex !in 0..size) throw IndexOutOfBoundsException(startIndex)
    val iterator = this.listIterator(startIndex)
    while (iterator.hasNext()) {
        if (predicate(iterator.next()))
            return iterator.previousIndex()
    }

    return -1
}

/**
 * Returns index of the last element matching the given [predicate] starting from [endIndex] (exclusive), or -1 if the
 * list does not contain such element.
 * @param endIndex The index after the last item to verify the [predicate] for.
 * @throws IndexOutOfBoundsException If [endIndex] is out of bounds of [this] list.
 */
@OptIn(ExperimentalContracts::class)
inline fun <T> List<T>.indexOfLast(endIndex: Int, predicate: (item: T) -> Boolean): Int {
    contract {
        callsInPlace(predicate)
    }

    if (endIndex !in 0..size) throw IndexOutOfBoundsException(endIndex)
    val iterator = this.listIterator(endIndex)
    while (iterator.hasPrevious()) {
        if (predicate(iterator.previous()))
            return iterator.nextIndex()
    }

    return -1
}

/**
 * Returns the first element matching the given [predicate] starting from the [startIndex] position (inclusive).
 * @param startIndex The index of the first item to verify the [predicate] for.
 * @throws IndexOutOfBoundsException if [startIndex] is out of bounds of [this] list.
 * @throws NoSuchElementException if no such element is found.
 */
@OptIn(ExperimentalContracts::class)
inline fun <T> List<T>.first(startIndex: Int, predicate: (item: T) -> Boolean): T {
    contract {
        callsInPlace(predicate)
    }

    return firstOrNull(startIndex, predicate)
        ?: throw NoSuchElementException("Collection contains no element matching the predicate.")
}

/**
 * Returns the first element matching the given [predicate] starting from the [startIndex] position (inclusive) or null
 * if no such element exists.
 * @param startIndex The index of the first item to verify the [predicate] for.
 * @throws IndexOutOfBoundsException if [startIndex] is out of bounds of [this] list.
 */
@OptIn(ExperimentalContracts::class)
inline fun <T> List<T>.firstOrNull(startIndex: Int, predicate: (item: T) -> Boolean): T? {
    contract {
        callsInPlace(predicate)
    }

    if (startIndex !in indices) throw IndexOutOfBoundsException(startIndex)
    val iterator = this.listIterator(startIndex)
    while (iterator.hasNext()) {
        val item = iterator.next()
        if (predicate(item))
            return item
    }
    return null
}

/**
 * Returns the last element matching the given [predicate] starting from the [endIndex] position (exclusive).
 * @param endIndex The index after the last item to verify the [predicate] for.
 * @throws IndexOutOfBoundsException if [endIndex] is out of bounds of [this] list.
 * @throws NoSuchElementException if no such element is found.
 */
@OptIn(ExperimentalContracts::class)
inline fun <T> List<T>.last(endIndex: Int, predicate: (item: T) -> Boolean): T {
    contract {
        callsInPlace(predicate)
    }

    return lastOrNull(endIndex, predicate)
        ?: throw NoSuchElementException("Collection contains no element matching the predicate.")
}

/**
 * Returns the last element matching the given [predicate] starting from the [endIndex] position (exclusive) or null
 * if no such element exists.
 * @param endIndex The index after the last item to verify the [predicate] for.
 * @throws IndexOutOfBoundsException if [endIndex] is out of bounds of [this] list.
 */
@OptIn(ExperimentalContracts::class)
inline fun <T> List<T>.lastOrNull(endIndex: Int, predicate: (item: T) -> Boolean): T? {
    contract {
        callsInPlace(predicate)
    }

    // as endIndex refers to the position one after the last item, endIndex == size is valid option
    if (endIndex !in 0..size) throw IndexOutOfBoundsException(endIndex)
    val iterator = this.listIterator(endIndex)
    while (iterator.hasPrevious()) {
        val item = iterator.previous()
        if (predicate(item))
            return item
    }
    return null
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
