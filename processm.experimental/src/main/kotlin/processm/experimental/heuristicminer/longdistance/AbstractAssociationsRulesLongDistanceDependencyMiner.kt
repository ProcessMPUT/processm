package processm.experimental.heuristicminer.longdistance

import processm.core.helpers.mapToSet
import processm.core.logging.logger
import processm.core.models.causalnet.Dependency
import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.Node
import processm.experimental.heuristicminer.longdistance.avoidability.AvoidabilityChecker

/**
 * An abstraction for a [LongDistanceDependencyMiner] with a hypothesis consisting of (positive) association rules,
 * i.e., `itemset->itemset`
 */
abstract class AbstractAssociationsRulesLongDistanceDependencyMiner(
    private val isAvoidable: AvoidabilityChecker
) : LongDistanceDependencyMiner {

    protected val log = ArrayList<List<Node>>()

    override fun processTrace(trace: List<Node>) {
        log.add(trace)
    }

    data class LongTermDependency(val premises: Set<Node>, val conclusions: Set<Node>)

    protected abstract val deps: Collection<LongTermDependency>


    private fun <E> intersection(data: List<Set<E>>): Set<E> {
        val result = HashSet<E>(data[0])
        for (i in 1 until data.size)
            result.retainAll(data[i])
        return result
    }

    private fun latestCommonPredecessor(inp: Set<Node>): Node {
        val tmp = ArrayList<List<Node>>()
        for (trace in log) {
            val start = inp.map { trace.indexOf(it) }.min()
            if (start != null && start != -1) {
                tmp.add(trace.subList(0, start))
            }
        }
        val common = intersection(tmp.map { it.toSet() })
        val latest = tmp.mapToSet { prefix -> prefix.last { common.contains(it) } }
        logger().debug("LATEST $latest")
        return latest.single()
    }

    override fun mine(currentModel: CausalNet): Collection<Dependency> {
        isAvoidable.setContext(currentModel)
        val result = ArrayList<Dependency>()
        for ((premises, conclusions) in deps) {
            if (!isAvoidable(premises to conclusions))
                continue
            logger().debug("MINE ${premises.map { it.activity }} -> ${conclusions.map { it.activity }}")
            if (premises.size == 1 && conclusions.size == 1) {
                //1-to-1 dependency
                val r = Dependency(premises.single(), conclusions.single())
                result.add(r)
            } else if (premises.size >= 2 && conclusions.size == 1) {
                //N-to-1 dependency
                val s = conclusions.single()
                val base = premises.map { Dependency(it, s) }
                result.addAll(base)
                result.addAll(premises.map { Dependency(it, currentModel.end) })
                result.add(Dependency(s, currentModel.end))
            } else if (premises.size == 1 && conclusions.size >= 2) {
                //1-to-N dependency
                val p = premises.single()
                val base = conclusions.map { Dependency(p, it) }
                result.addAll(base)
                result.addAll(conclusions.map { Dependency(currentModel.start, it) })
                result.add(Dependency(currentModel.start, p))
            } else {
                //N-to-M dependency
                //honestly, I'm not sure if this is sufficient
                val lcp = latestCommonPredecessor(premises)
                result.addAll(conclusions.map { Dependency(lcp, it) })
                result.addAll(premises.map { Dependency(lcp, it) })
            }
        }
        if (logger().isDebugEnabled)
            result.forEach { logger().debug("FINAL ${it.source.activity} ${it.target.activity}") }
        return result
    }
}