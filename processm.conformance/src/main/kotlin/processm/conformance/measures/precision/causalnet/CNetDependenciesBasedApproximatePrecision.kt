package processm.conformance.measures.precision.causalnet

import processm.core.helpers.Counter
import processm.core.helpers.mapToSet
import processm.core.logging.debug
import processm.core.logging.logger
import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.Node
import processm.core.models.commons.Activity


/**
 * This will never exceed the real precision, as it overestimates the set of possible activities taking only splits into account,
 * not simulating what is actually possible
 */
class CNetDependenciesBasedApproximatePrecision(model: CausalNet) : CNetAbstractPrecision(model) {

    companion object {
        private val logger = logger()
    }

    override fun availableActivities(prefix: List<Activity>): Set<Node> {
        if (prefix.isEmpty())
            return followSilents(setOf(model.start), HashSet())

        val ctr = Counter<Node>()
        for ((idx, activity) in prefix.withIndex()) {
            if (idx >= 1) {
                assert(ctr[activity as Node] >= 1)
                ctr.dec(activity)
            }
            for (produce in model.outgoing[activity].orEmpty())
                ctr.inc(produce.target)
        }
        logger.debug { "$prefix -> $ctr" }
        return followSilents(ctr.entries.filter { it.value >= 1 && !it.key.isSilent }.mapToSet { it.key }, HashSet())
    }
}