package processm.conformance.measures.precision.causalnet

import processm.core.helpers.Counter
import processm.core.helpers.intersect
import processm.core.logging.debug
import processm.core.logging.logger
import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.Dependency
import processm.core.models.causalnet.Node
import processm.core.models.commons.Activity


/**
 * This will never exceed the real precision, as it overestimates the set of possible activities taking only splits into account,
 * not simulating what is actually possible
 */
class CNetBindingsBasedApproximatePrecision(model: CausalNet) : CNetApproximatePrecision(model) {

    companion object {
        private val logger = logger()
    }

    private val mustConsume = model.joins.mapValues { (_, joins) -> intersect(joins.map { join -> join.dependencies }) }
    private val mayProduce = model.outgoing

    override fun availableActivities(prefix: List<Activity>): Set<Node> {
        if (prefix.isEmpty())
            return followSilents(setOf(model.start), HashSet())

        val ctr = Counter<Dependency>()
        for (activity in prefix) {
            mustConsume[activity]?.forEach(ctr::dec)
            mayProduce[activity]?.forEach(ctr::inc)
        }
        logger.debug { "$prefix -> $ctr" }
        val candidates = ctr
            .filter { it.value > 0 }
            .keys
            .groupBy { it.target }
            .filter { (target, active) ->
                model.joins[target].orEmpty().any { join -> active.containsAll(join.dependencies) }
            }
            .keys
        return followSilents(candidates, HashSet())
    }
}