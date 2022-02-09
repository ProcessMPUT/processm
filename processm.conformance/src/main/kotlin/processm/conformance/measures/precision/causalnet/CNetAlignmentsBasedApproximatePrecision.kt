package processm.conformance.measures.precision.causalnet

import processm.conformance.models.alignments.Alignment
import processm.core.helpers.TrieCounter
import processm.core.helpers.mapToSet
import processm.core.logging.debug
import processm.core.logging.logger
import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.DecoupledNodeExecution
import processm.core.models.causalnet.Node
import processm.core.models.commons.Activity

/**
 * An upper bound for precision - it only takes into account these activities that are available according to the given alignments
 *
 * It never underestimates: all activities available according to the alignments are possible for a given prefix,
 * but there may be some other executions of the prefix yielding different available activities.
 */
class CNetAlignmentsBasedApproximatePrecision(model: CausalNet) : CNetApproximatePrecision(model) {

    companion object {
        private val logger = logger()
    }

    private val availableActivities = TrieCounter<Activity, HashSet<Node>> { HashSet() }

    override fun availableActivities(prefix: List<Activity>): Set<Activity> {
        var current = availableActivities
        for (activity in prefix)
            current = current[activity]
        return current.value
    }

    /**
     * [translate] assumes that it is called exactly once per call to [invoke].
     */
    override fun translate(alignments: Sequence<Alignment>): Sequence<List<Activity>> = sequence {
        val instance = model.createInstance()
        availableActivities.clear()
        availableActivities.update { dest ->
            val tmp = instance.availableActivities.mapToSet { (it as DecoupledNodeExecution).activity }
            followSilents(tmp, dest)
            return@update dest
        }
        for (alignment in alignments) {
            logger.debug { alignment.toString() }
            val prefix = ArrayList<Activity>()
            var current = availableActivities
            for (step in alignment.steps) {
                if (step.modelMove != null) {
                    val activity = (step.modelMove as DecoupledNodeExecution).activity
                    prefix.add(activity)
                    val next = current[activity]
                    next.update { dest ->
                        instance.setState(step.modelState)
                        instance.availableActivities.mapTo(dest) { (it as DecoupledNodeExecution).activity }
                        return@update dest
                    }
                    current = next
                }
            }
            yield(prefix)
        }
    }
}