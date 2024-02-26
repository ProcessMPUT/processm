package processm.conformance.measures.precision

import processm.conformance.measures.Measure
import processm.core.log.hierarchical.Log
import processm.core.models.commons.Activity
import processm.core.models.petrinet.PetriNet
import processm.core.models.petrinet.PetriNetInstance
import processm.helpers.Trie
import processm.logging.logger
import processm.logging.trace

/**
 * An approximate precision for [PetriNet]s following Jorge Munoz-Gama, Josep Carmona: A Fresh Look at Precision in Process Conformance. BPM 2010: 211-226
 */
class ETCPrecision(val model: PetriNet) : Measure<Log, Double> {

    companion object {
        private val logger = logger()
    }

    private data class Payload(var counter: Int, val available: HashSet<Activity>)

    private val activities = model.activities.filter { !it.isSilent }.groupBy { it.name }

    /**
     * If there's exactly one activity in the intersection of [activities] and the activities available in [instance], return that activity.
     * Otherwise, if there's exactly one silent available activity, execute it and reconsider from the beginning.
     * Otherwise, throw an exception.
     */
    private fun pushForward(instance: PetriNetInstance, activities: List<Activity>): Activity {
        while (true) {
            val available = instance.availableActivities.toList()
            if (available.isEmpty())
                throw IllegalStateException("There are no activities available for execution")
            val candidates = available.filter(activities::contains)
            when {
                candidates.size == 1 -> return candidates.single()
                candidates.size > 1 -> throw IllegalStateException("Multiple activities with the same name are ready for execution. This requires nondeterministic execution and is thus unsupported.")
                else -> {
                    assert(candidates.isEmpty())
                    val silent = available.single(Activity::isSilent)
                    instance.getExecutionFor(silent).execute()
                }
            }
        }
    }

    private fun buildTransitionSystem(artifact: Log): Trie<Activity, Payload> {
        val transitionSystem = Trie<Activity, Payload> { Payload(0, HashSet()) }
        val instance = model.createInstance()
        for (trace in artifact.traces) {
            instance.setState(null)
            var current = transitionSystem
            for (activities in trace.events.mapNotNull { activities[it.conceptName] }) {
                val activity = pushForward(instance, activities)
                current.value.counter++
                current.value.available.addAll(instance.availableActivities.filter { !it.isSilent })
                current = current.getOrPut(activity)
                logger.trace { "${activity.name} ${instance.availableActivities.toList()}" }
                instance.getExecutionFor(activity).execute()
            }
            logger.trace("")
        }
        return transitionSystem
    }

    override fun invoke(artifact: Log): Double {
        val transitionSystem = buildTransitionSystem(artifact)
        var nom = 0
        var den = 0
        for (state in transitionSystem) {
            val n = state.trie.value.counter
            assert(n >= 0)
            if (n > 0) {
                val at = state.trie.value.available
                val rt = state.trie.children.keys
                assert(at.containsAll(rt)) { "prefix=${state.prefix} At=$at Rt=$rt" }
                val eeSize = at.count { it !in rt }
                nom += n * eeSize
                den += n * at.size
            }
        }
        return 1.0 - nom.toDouble() / den
    }
}
