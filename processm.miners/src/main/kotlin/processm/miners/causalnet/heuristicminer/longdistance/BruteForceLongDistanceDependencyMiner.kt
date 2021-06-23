package processm.miners.causalnet.heuristicminer.longdistance

import processm.core.helpers.allSubsets
import processm.core.models.causalnet.Node
import processm.miners.causalnet.heuristicminer.longdistance.avoidability.AvoidabilityChecker
import java.util.*
import kotlin.math.max

/**
 * Long-distance dependency mining by checking all possible sets of premises up to the size specified by [maxPremisesSize].
 * Seems to be sound and complete (given large enough [maxPremisesSize]) within the hypothesis space of positive association rules (i.e., in general, is incomplete).
 */
class BruteForceLongDistanceDependencyMiner(
    isAvoidable: AvoidabilityChecker,
    val maxPremisesSize: Int = 3
) :
    AbstractAssociationsRulesLongDistanceDependencyMiner(isAvoidable) {


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
        queue.add(
            Args(
                setOf(),
                0,
                log.indices.map { it to -1 }.toList(),
                listOf()
            )
        )
        while (queue.isNotEmpty()) {
            val arg = queue.pollFirst()
            if (arg.negative.isNotEmpty()) {
                val known =
                    arg.prec.allSubsets(true)
                        .map { rules.getOrDefault(it.toSet(), setOf()) }
                        .flatten()
                val succ = intersection(arg.positive.map {
                    val t = log[it.first]
                    t.subList(it.second, t.size).toSet()
                }) - arg.prec - known - common
                if (succ.isNotEmpty()) {
                    rules[arg.prec] = succ
                }
            }
            val extensions = nodes.subList(arg.pos, nodes.size)
            if (arg.prec.size + 1 <= maxPremisesSize) {
                for ((idx, ext) in extensions) {
                    val newPos = ArrayList<Pair<Int, Int>>()
                    val newNeg = ArrayList<Int>()
                    for ((tidx, last) in arg.positive) {
                        val extpos = log[tidx].indexOfFirst { it == ext }
                        if (extpos >= 0)
                            newPos.add(tidx to max(last, extpos))
                        else
                            newNeg.add(tidx)
                    }
                    if (newPos.isNotEmpty()) {
                        queue.addLast(
                            Args(
                                arg.prec + ext,
                                idx + 1,
                                newPos,
                                arg.negative + newNeg
                            )
                        )
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