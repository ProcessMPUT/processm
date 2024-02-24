package processm.helpers

import java.util.*

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
