package processm.helpers

/**
 * A 2-dimensional array of doubles of the type [n]x[m] backed by a single [DoubleArray]
 */
class DoubleArray2D(val n: Int, val m: Int) {
    private val data = DoubleArray(n * m)

    operator fun get(x: Int, y: Int) = data[x * m + y]

    operator fun set(x: Int, y: Int, v: Double) {
        data[x * m + y] = v
    }
}