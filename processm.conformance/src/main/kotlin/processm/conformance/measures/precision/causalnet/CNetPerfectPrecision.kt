package processm.conformance.measures.precision.causalnet

import processm.core.logging.debug
import processm.core.logging.logger
import processm.core.logging.trace
import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.Node
import processm.core.models.commons.Activity
import processm.core.verifiers.causalnet.CausalNetVerifierImpl


class CNetPerfectPrecision(model: CausalNet) : CNetAbstractPrecision(model) {

    companion object {
        private val logger = logger()
    }

    override fun availableActivities(prefix: List<Activity>): Set<Activity> {
        logger.debug { "possibleNext($prefix)" }
        val result = HashSet<Node>()
        val verifier = CausalNetVerifierImpl(model)
        // valid sequences runs BFS and there is only a finite number of possible successors, so I think this terminates
        val seqs = verifier.computeSetOfValidSequences(false) { seq, _ ->
            val activities = seq.mapNotNull { if (!it.a.isSilent) it.a else null }
            if (activities.size <= prefix.size)
                return@computeSetOfValidSequences activities == prefix.subList(0, activities.size)
            return@computeSetOfValidSequences activities[prefix.size] !in result
        }
        for (seq in seqs) {
            val activities = seq.mapNotNull { if (!it.a.isSilent) it.a else null }
            if (activities.size > prefix.size) {
                logger.trace { "$activities" }
                assert(activities.subList(0, prefix.size) == prefix)
                result.add(activities[prefix.size])
            }
        }
        return result
    }


}