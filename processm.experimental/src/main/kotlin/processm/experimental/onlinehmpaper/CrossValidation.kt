package processm.experimental.onlinehmpaper

import processm.core.log.Event
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import kotlin.random.Random


data class LogStats(val nTraces: Int, val nNames: Int)

fun sample(
    log: Log,
    random: Random,
    knownNamesThreshold: Int,
    missThreshold: Int
): Triple<Log, LogStats, LogStats> {
    val allTraces = log.traces.toList()
    val allNames = allTraces.flatMap { trace -> trace.events.map { it.conceptName }.toSet() }.toSet()
    val completeStats = LogStats(allTraces.size, allNames.size)
    println("# distinct names: ${allNames.size}")
    if (allNames.size <= knownNamesThreshold)
        return Triple(log, completeStats, completeStats)
    var knownNames = emptySet<String?>()
    val available = allTraces.indices.toMutableList()
    var ctr = 0
    while (true) {
        val idx = random.nextInt(available.size)
        val trace = allTraces[available[idx]]
        available.removeAt(idx)
        val newKnownNames = knownNames + trace.events.map { it.conceptName }.toSet()
        if (newKnownNames.size > knownNamesThreshold) {
            ctr++
            if (ctr < missThreshold)
                continue
            else
                break
        }
        knownNames = newKnownNames
    }
    println("Selected ${knownNames.size} events: $knownNames")
    val selectedTraces =
        log.traces.filter { trace -> knownNames.containsAll(trace.events.map { it.conceptName }.toList()) }
    println("Selected traces: ${selectedTraces.count()}")
    return Triple(Log(selectedTraces), completeStats, LogStats(selectedTraces.count(), knownNames.size))
}

fun cvRanges(n: Int, k: Int): List<Pair<Int, Int>> {
    val step = n / k
    val overhead = n - step * k
    check(overhead < k)
    val ranges = ArrayList<Pair<Int, Int>>()
    var start = 0
    for (i in 0 until k) {
        val end = start + step + if (i < overhead) 1 else 0
        ranges.add(start to end)
        start = end
    }
    check(start == n)
    println(ranges)
    val lengths = ranges.map { (s, e) -> e - s }
    println(lengths)
    return ranges
}

fun cvLog(log: Log, k: Int, rnd: Random, start: Event, end: Event): Sequence<Pair<Log, Log>> = sequence {
    val allTraces = log.traces.toList().shuffled(rnd)
    for (testRange in cvRanges(allTraces.size, k)) {
        val before = allTraces.subList(0, testRange.first)
        val after = allTraces.subList(testRange.second, allTraces.size)
        val test = allTraces.subList(testRange.first, testRange.second)
        check(before.size + after.size + test.size == allTraces.size)
        val trainLog = Log(before.asSequence() + after.asSequence())
        val testLog = Log(test.map { Trace(sequenceOf(start) + it.events + sequenceOf(end)) }.asSequence())
        yield(trainLog to testLog)
    }
}