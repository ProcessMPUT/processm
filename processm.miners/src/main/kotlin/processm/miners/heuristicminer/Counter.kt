package processm.miners.heuristicminer

typealias Counter<K> = HashMap<K, Int>

fun <K> Counter<K>.inc(key: K, n: Int = 1) {
    this[key] = this.getOrDefault(key, 0) + n
}

fun <K> Counter<K>.inc(keys: Collection<K>) {
    keys.forEach { inc(it) }
}