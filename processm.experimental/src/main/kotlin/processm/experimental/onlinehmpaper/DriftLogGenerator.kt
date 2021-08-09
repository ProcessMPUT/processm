package processm.experimental.onlinehmpaper

import processm.core.helpers.mapToSet
import processm.core.log.hierarchical.InMemoryXESProcessing
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import kotlin.random.Random


private fun groupByVariant(log: Log) = log.traces.groupBy { trace ->
    trace
        .events
        .filter {
            it.lifecycleTransition === null || "complete".equals(
                it.lifecycleTransition,
                ignoreCase = true
            )
        }
        .map { it.conceptName.toString() }
        .toSet()
}

private fun <T> sim(a: Set<T>, b: Set<T>): Double {
    val ab = a.intersect(b).size
    return ab.toDouble() / (a.size + b.size - ab)
}

private fun <T> ahc(
    objects: List<T>,
    k: Int,
    sizes: List<Int>,
    maxSize: Int,
    sim: (a: T, b: T) -> Double
): List<Set<T>> {
    fun dist(ca: Set<Int>, cb: Set<Int>): Double =
        1.0 - ca.map { a -> cb.map { b -> sim(objects[a], objects[b]) }.minOrNull()!! }.minOrNull()!!

    val clusters: ArrayList<MutableSet<Int>> = objects.indices.mapTo(ArrayList()) { mutableSetOf(it) }
    val csizes: ArrayList<Int> = ArrayList(sizes)
    val distances = MutableList(clusters.size) { MutableList(clusters.size) { 0.0 } }
    for (i in clusters.indices) {
        for (j in (i + 1) until clusters.size) {
            val d = dist(clusters[i], clusters[j])
            distances[i][j] = d
            distances[j][i] = d
        }
    }
    while (clusters.size > k) {
        var nearest = Double.POSITIVE_INFINITY
        var nearesti: Int = -1
        var nearestj: Int = -1
//            val avail = clusters.indices.filter { csizes[it] <minSize } //.ifEmpty { clusters.indices.toList() }
//            check(avail.isNotEmpty())
        for (th in listOf(maxSize, Integer.MAX_VALUE)) {
            for (i in clusters.indices) {
                for (j in i + 1 until clusters.size) {
                    val d = distances[i][j]
                    if (d < nearest && csizes[i] + csizes[j] <= th) {
                        nearest = d
                        nearesti = i
                        nearestj = j
                    }
                }
            }
            if (nearest < Double.POSITIVE_INFINITY)
                break
        }
        check(nearest < Double.POSITIVE_INFINITY && nearesti >= 0 && nearestj > nearesti)
        clusters[nearesti].addAll(clusters[nearestj])
        csizes[nearesti] += csizes[nearestj]
//            println("$nearesti += $nearestj d=$nearest expsize=${csizes[nearesti]} truesize=${clusters[nearesti].map { sizes[it] }
//                .sum()}")
        val lastCluster = clusters.removeLast()
        val lastClusterSize = csizes.removeLast()
        val recompute = mutableListOf(nearesti)
        if (nearestj < clusters.size) {
            clusters[nearestj] = lastCluster
            csizes[nearestj] = lastClusterSize
            recompute.add(nearestj)
        }
        for (i in recompute) {
            for (j in clusters.indices) {
                if (i != j) {
                    val d = dist(clusters[i], clusters[j])
                    distances[i][j] = d
                    distances[j][i] = d
                }
            }
        }
    }
    println("csizes=$csizes")
    return clusters.map { cl -> cl.mapToSet { objects[it] } }
}

@InMemoryXESProcessing
fun createDriftLogs(
    completeLog : Log,
    sampleSeed: Long,
    splitSeed: Long,
    k: Int,
    knownNamesThreshold: Int,
    missThreshold: Int
): List<List<Trace>> {
    val (partialLog, completeLogStats, partialLogStats) = sample(
        completeLog,
        Random(sampleSeed),
        knownNamesThreshold,
        missThreshold
    )
    val byVariant = groupByVariant(partialLog)
    val rnd = Random(splitSeed)
    val variants = byVariant.entries.sortedBy { -it.value.size }
    val nTraces = variants.map { it.value.size }.sum()
    val clusters =
        ahc(variants.map { it.key }, 2 * k, variants.map { it.value.size }, nTraces / (2 * k))
        { a, b -> sim(a, b) }
            //.sortedByDescending { cl -> cl.map { byVariant.getValue(it).size }.sum() }
            .shuffled(rnd)
    for (cl in clusters) {
        val traces = cl.map { byVariant.getValue(it).size }
        println("${cl.size} ${traces.sum()}")
        println("\t$traces")
    }
    val clusteredTraces = clusters.map { cl -> cl.flatMap { byVariant.getValue(it) } }
    if (clusteredTraces.size != 2 * k) {
        throw IllegalStateException("Clustering produced ${clusteredTraces.size} clusters instead of ${2 * k} clusters. Ignoring log file.")
    }
    val coreLog = clusteredTraces
        .subList(0, k + 1)
        .flatten()
        .shuffled(rnd)
    val coreLogParts = cvRanges(coreLog.size, k + 1).map { (from, to) -> coreLog.subList(from, to) }
    val driftLogParts = clusteredTraces.subList(k + 1, clusters.size)
    val partialLogs =
        (listOf(coreLogParts[0] + coreLogParts[1]) + driftLogParts.mapIndexed { idx, drift -> coreLogParts[idx + 2] + drift })
            .map { it.shuffled(rnd) }
    return partialLogs
}
