package processm.miners.heuristicminer

internal class SquareMatrix<T>(val n: Int, zero: T) {
    private val data = ArrayList<T>((1..n * n).map { zero })
    operator fun get(x: Int, y: Int): T = data[y * n + x]
    operator fun set(x: Int, y: Int, v: T) {
        data[y * n + x] = v
    }

    override fun toString(): String {
        return (0 until n).joinToString("\n ", "[", "]") { y ->
            data.subList(y * n, y * n + n).joinToString(", ", "[", "]")
        }
    }
}

inline fun <T, U> Sequence<Pair<T, U>>.forEachPair(action: (T, U) -> Unit): Unit {
    forEach { action(it.first, it.second) }
}

fun <T, U> Sequence<Pair<T, U>>.filterPairs(predicate: (T, U) -> Boolean): Sequence<Pair<T, U>> {
    return filter { predicate(it.first, it.second) }
}

fun <T, U> Sequence<Pair<T, U>>.anyPair(predicate: (T, U) -> Boolean): Boolean {
    return any { predicate(it.first, it.second) }
}
//
//fun <T, U, V> Sequence<Pair<T, U>>.mapPairs(transform: (T, U) -> V): Sequence<V> {
//    return map { transform(it.first, it.second) }
//}