package processm.miners.heuristicminer

import processm.core.models.causalnet.Node
import processm.miners.heuristicminer.avoidability.AvoidabilityChecker
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet
import kotlin.math.max

class BruteForceLongTermDependencyMiner(
    isAvoidable: AvoidabilityChecker,
    val maxPredecessorLength: Int = 3
) :
    AbstractAssociationsRulesLongTermDependencyMiner(isAvoidable) {


    private val nodes by lazy {
        log.flatten().toSet().toList()
            .mapIndexed { index, node -> index to node }
    }

    private fun <E> intersection(data: List<Set<E>>): Set<E> {
        val result = HashSet<E>(data[0])
        for (i in 1 until data.size)
            result.retainAll(data[i])
        return result
    }

    private data class Args(
        val prec: Set<Node>,
        val pos: Int,
        val positive: List<Pair<Int, Int>>,
        val negative: List<Int>
    )

    private fun helper(): List<LongTermDependency> {
        val common = intersection(log.map { it.toSet() })
        val rules = HashMap<Set<Node>, Set<Node>>()
        val queue = ArrayDeque<Args>()
        queue.add(Args(setOf(), 0, log.indices.map { it to -1 }.toList(), listOf()))
        while (queue.isNotEmpty()) {
            val (prec, pos, positive, negative) = queue.pollFirst()
            if (negative.isNotEmpty()) {
                val known =
                    prec.allSubsets().filter { it.isNotEmpty() }.toList().map { rules.getOrDefault(it, setOf()) }
                        .flatten()
                val succ = intersection(positive.map {
                    val t = log[it.first]
                    t.subList(it.second, t.size).toSet()
                }) - prec - known - common
                if (succ.isNotEmpty()) {
                    rules[prec] = succ
                    println("\t$prec -> $succ")
                }
            }
            val extensions = nodes.subList(pos, nodes.size)
            if (prec.size + 1 <= maxPredecessorLength) {
                for ((idx, ext) in extensions) {
                    val newPos = ArrayList<Pair<Int, Int>>()
                    val newNeg = ArrayList<Int>()
                    for ((tidx, last) in positive) {
                        val extpos = log[tidx].indexOfFirst { it == ext }
                        if (extpos >= 0)
                            newPos.add(tidx to max(last, extpos))
                        else
                            newNeg.add(tidx)
                    }
                    if (newPos.isNotEmpty()) {
                        queue.addLast(Args(prec + ext, idx + 1, newPos, negative + newNeg))
                    }
                }
            }
        }
        return rules.map { (k, v) -> LongTermDependency(k, v) }
    }

    override val deps by lazy {
        helper()
    }
}