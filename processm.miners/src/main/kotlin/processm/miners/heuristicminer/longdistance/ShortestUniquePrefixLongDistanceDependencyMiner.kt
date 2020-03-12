package processm.miners.heuristicminer.longdistance

import processm.core.models.causalnet.Node
import processm.miners.heuristicminer.avoidability.AvoidabilityChecker

/**
 * A proof-of-concept of an approach finding shortest common prefixes such that the remainder the traces sharing the prefix
 * are identical barring order and repetitions. Unfortunately, this approach is invalid (e.g., it fails if there are two
 * identical, independent processes in a sequence - it is able to find dependencies in the second, but not in the first).
 * It is left here just in case, but currently there is no reason to believe this code will be useful.
 */
private class ShortestUniquePrefixLongDistanceDependencyMiner(isAvoidable: AvoidabilityChecker) :
    AbstractAssociationsRulesLongDistanceDependencyMiner(isAvoidable) {

    private val seen = HashSet<Set<Node>>()

    private fun helper(
        prefix: Set<Node>,
        traces: List<Int>,
        log: List<List<Node>>
    ): List<Triple<Set<Node>, Set<Node>, List<Int>>> {
        if (seen.contains(prefix))
            return listOf()
        seen.add(prefix)
        println("$prefix -> $traces")
        val pos = prefix.size + 1 //+1 to avoid common start
        //TODO ignore common here, not later -> requires independend controlling pos
        //TODO rewrite unique checking
        val suffixes = traces.map { log[it].subList(pos, log[it].size).toSet() }.groupBy { it.hashCode() }
        if (suffixes.size == 1) {
            val flat = suffixes.values.flatten().toSet()
            if (flat.size == 1) {
//                val lhs = (prefix - common).toSet()
//                val rhs = (flat.single() - common).toSet()
                val lhs = prefix.toSet()
                val rhs = flat.single().toSet()
                if (lhs.isNotEmpty() && rhs.isNotEmpty())
                    return listOf(Triple(lhs, rhs, traces))
                else
                    return listOf()
            }
            println("END")
        }
        return traces
            .groupBy { log[it][pos] }
            .flatMap { (extension, exttraces) ->
                helper(prefix + extension, exttraces, log)
            }
    }


    private fun <E> intersection(data: List<Set<E>>): Set<E> {
        val result = HashSet<E>(data[0])
        for (i in 1 until data.size)
            result.retainAll(data[i])
        return result
    }

    private fun <E> Set<E>.subsetOf(superset: Set<E>): Boolean {
        return superset.containsAll(this)
    }

    private fun prune(positive: Set<Node>, negative: List<Set<Node>>): Set<Node> {
        assert(!negative.any { positive.subsetOf(it) })
        var current = positive
        for (node in positive) {
            var next = current - setOf(node)
            if (!negative.any { next.subsetOf(it) })
                current = next
        }
        println("PRUNED $positive TO $current")
        return current
    }

    private fun prune(positive: List<Set<Node>>, negative: List<Set<Node>>): List<Set<Node>> {
        return positive.map { prune(it, negative) }
    }

    private fun filter(trace: List<Node>, rules: Set<Pair<Set<Node>, Set<Node>>>): List<Node> {
        var remove = HashSet<Node>()
        for (rule in rules)
            if (trace.containsAll(rule.first)) {
                remove.addAll(rule.first)
                remove.addAll(rule.second)
            }
        return trace - remove
    }

    private fun computeDeps(): Set<Pair<Set<Node>, Set<Node>>> {
        var log: List<List<Node>> = this.log
        var result = HashSet<Pair<Set<Node>, Set<Node>>>()
        while (true) {
            val intermediate = helper(setOf(), log.indices.toList(), log)
            val allPrefixes = intermediate.map { it.first }
            val partial = intermediate
                .groupBy { it.second }
                .map { (suffix, deps) ->
                    val prefixes = deps.map { it.first }
                    val traces = deps.map { it.third }
                    val negativePrefixes = allPrefixes - prefixes
                    val pruned = prune(prefixes, negativePrefixes)
                    pruned.map { prefix -> prefix to suffix }
//                TODO("z prefixes trzeba zbudować taką alternatywę koniunkcji, która jednoznacznie oddziela te traces od wszystkich pozostałych")
                }
                .flatten()
                .toSet()
            if (partial.isEmpty())
                break
            result.addAll(partial)
            log = log.map { filter(it, partial) }
            println()
            log.forEach { println("SHORTER TRACE $it") }
        }
        return result
    }

    override val deps by lazy {
        computeDeps().map { LongTermDependency(it.first, it.second) }
    }

}