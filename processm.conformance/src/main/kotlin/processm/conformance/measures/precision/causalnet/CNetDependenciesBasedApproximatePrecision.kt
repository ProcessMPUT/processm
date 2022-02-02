package processm.conformance.measures.precision.causalnet

import processm.conformance.measures.Measure
import processm.core.helpers.Counter
import processm.core.helpers.mapToSet
import processm.core.log.hierarchical.Log
import processm.core.logging.logger
import processm.core.logging.trace
import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.Node

internal class CNetDependenciesBasedApproximatePrecisionAux(
    log: Log,
    model: CausalNet
) : CNetPrecisionAux(log, model) {


    companion object {
        private val logger = logger()
    }

    private fun followSilents(nodes: Iterable<Node>, result: HashSet<Node>): Set<Node> {
        for (node in nodes)
            if (node.isSilent)
                followSilents(model.outgoing[node]?.map { it.target }.orEmpty(), result)
            else
                result.add(node)
        return result
    }

    override fun possibleNext(prefix: List<Node>): Set<Node> {
        if (prefix.isEmpty())
            return followSilents(setOf(model.start), HashSet())

        val ctr = Counter<Node>()
        prefix.flatMap { model.outgoing[it]?.map { dep -> dep.target }.orEmpty() }.groupingBy { it }.eachCountTo(ctr)
        //from 1, because the first activity is not a result of any split
        for (n in prefix.subList(1, prefix.size)) {
            ctr.compute(n) { _, v ->
                v!! - 1
            }
        }
        logger.trace { "$prefix -> $ctr" }
        return followSilents(ctr.entries.filter { it.value >= 1 && !it.key.isSilent }.mapToSet { it.key }, HashSet())
    }

}

/**
 * This will never exceed the real precision, as it overestimates the set of possible activities taking only splits into account,
 * not simulating what is actually possible
 */
class CNetDependenciesBasedApproximatePrecision(val model: CausalNet) : Measure<Log, Double> {

    override fun invoke(artifact: Log): Double =
        CNetDependenciesBasedApproximatePrecisionAux(artifact, model).precision

}