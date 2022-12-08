package processm.dbmodels

import java.io.Serializable

/**
 * Represents a triad of values
 *
 * This class is the same as [kotlin.Triple] but [Comparable].
 *
 * @param A type of the first value.
 * @param B type of the second value.
 * @param C type of the third value.
 * @property first First value.
 * @property second Second value.
 * @property third Third value.
 */
data class ComparableTriple<A, B, C>(
    val first: A,
    val second: B,
    val third: C
) : Serializable, Comparable<ComparableTriple<A, B, C>>
        where A : Comparable<A>, B : Comparable<B>, C : Comparable<C> {
    override fun compareTo(other: ComparableTriple<A, B, C>): Int {
        val cmp1 = first.compareTo(other.first)
        if (cmp1 != 0)
            return cmp1
        val cmp2 = second.compareTo(other.second)
        if (cmp2 != 0)
            return cmp2
        val cmp3 = third.compareTo(other.third)
        return cmp3
    }

    /**
     * Returns string representation of the [Triple] including its [first], [second] and [third] values.
     */
    override fun toString(): String = "($first, $second, $third)"
}

/**
 * Converts this triple into a list.
 */
fun <T : Comparable<T>> ComparableTriple<T, T, T>.toList(): List<T> = listOf(first, second, third)
