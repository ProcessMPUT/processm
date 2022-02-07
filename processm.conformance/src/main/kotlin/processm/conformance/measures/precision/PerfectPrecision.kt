package processm.conformance.measures.precision

import processm.conformance.measures.Measure
import processm.core.helpers.TrieCounter
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.logging.debug
import processm.core.logging.logger
import processm.core.models.causalnet.CausalNet
import processm.core.models.commons.Activity
import processm.core.models.commons.ProcessModel
import processm.core.models.commons.ProcessModelInstance


class PerfectPrecision(val model: ProcessModel) : Measure<Log, Double> {

    companion object {
        private val logger = logger()
    }

    private val name2Activity = model.activities.filter { !it.isSilent }.associateBy { it.name }

    init {
        require(model !is CausalNet) { "PerfectPrecision cannot handle causal nets. Use processm.conformance.measures.precision.causalnet.CNetPerfectPrecision instead." }
    }

    internal fun availableActivities(prefix: List<Activity>): Set<Activity> =
        availableActivities(prefix, model.createInstance()).toSet()

    private fun availableActivities(prefix: List<Activity>, instance: ProcessModelInstance): Sequence<Activity> =
        sequence {
            if (prefix.isEmpty())
                yieldAll(instance.availableActivities.filter { !it.isSilent }.toSet())
            else {
                val first = prefix[0]
                val rest = prefix.subList(1, prefix.size)
                val originalState = instance.currentState.copy()
                for (activity in instance.availableActivities) {
                    if (activity.isSilent) {
                        instance.setState(originalState.copy())
                        instance.getExecutionFor(activity).execute()
                        yieldAll(availableActivities(prefix, instance))
                    } else if (first == activity) {
                        instance.setState(originalState.copy())
                        instance.getExecutionFor(activity).execute()
                        yieldAll(availableActivities(rest, instance))
                    }
                }
            }
        }

    private fun translate(trace: Trace): List<Activity> =
        trace.events.mapNotNull { e -> name2Activity[e.conceptName] }.toList()

    override fun invoke(artifact: Log): Double {
        val observed = TrieCounter<Activity, Int> { 0 }
        for (trace in artifact.traces) {
            var current = observed
            current.update { it + 1 }
            for (activity in translate(trace)) {
                current = current[activity]
                current.update { it + 1 }
            }
        }
        var nom = 0.0
        var den = 0
        for ((prefix, total, children) in observed) {
            val availableActivities = availableActivities(prefix)
            logger.debug { "$total $prefix $observed/$children" }
            if (availableActivities.isNotEmpty()) {
                nom += total * children.intersect(availableActivities).size.toDouble() / availableActivities.size
                den += total
            }
        }
        return if ((nom == 0.0) && (den == 0))
            0.0
        else
            nom / den
    }

}