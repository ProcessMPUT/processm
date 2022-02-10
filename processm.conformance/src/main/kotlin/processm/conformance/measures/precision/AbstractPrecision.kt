package processm.conformance.measures.precision

import processm.conformance.measures.Measure
import processm.conformance.models.alignments.AStar
import processm.conformance.models.alignments.Alignment
import processm.core.helpers.TrieCounter
import processm.core.log.hierarchical.Log
import processm.core.logging.debug
import processm.core.logging.logger
import processm.core.models.commons.Activity
import processm.core.models.commons.ProcessModel

abstract class AbstractPrecision(open val model: ProcessModel) : Measure<Any, Double> {

    companion object {
        private val logger = logger()
    }

    internal data class Aux(var total: Int, var available: Set<Activity>? = null)

    internal open fun availableActivities(prefixes: TrieCounter<Activity, Aux>) {
        prefixes.forEach { (prefix, trie) ->
            trie.update {
                it.available = availableActivities(prefix)
                return@update it
            }
        }
    }

    abstract fun availableActivities(prefix: List<Activity>): Set<Activity>

    open fun translate(alignments: Sequence<Alignment>): Sequence<List<Activity>> =
        alignments.map { alignment -> alignment.steps.mapNotNull { it.modelMove } }

    override fun invoke(artifact: Any): Double =
        when (artifact) {
            is Sequence<*> -> this(artifact)
            is Log -> this(artifact)
            else -> throw IllegalArgumentException("Artifact must be either Sequence<Alignment> or Log")
        }

    open operator fun invoke(artifact: Log): Double =
        this(AStar(model).align(artifact)) //TODO replace AStar with CompositeAligner once #134 is fixed

    open operator fun invoke(artifact: Sequence<Alignment>): Double {
        val observed = TrieCounter<Activity, Aux> { Aux(0) }
        for (trace in translate(artifact)) {
            var current = observed
            for (activity in trace) {
                // update is on purpose first - we update the counter for an empty prefix, but we don't update the counter for the prefix = trace
                current.update {
                    it.total += 1
                    return@update it
                }
                current = current[activity]
            }
        }
        availableActivities(observed)
        var nom = 0.0
        var den = 0
        for ((prefix, trie, children) in observed) {
            val (total, availableActivities) = trie.value
            logger.debug { "$total $prefix $children/$availableActivities" }
            if (total > 0 && availableActivities !== null && availableActivities.isNotEmpty()) {
                nom += total * children.intersect(availableActivities).size.toDouble() / availableActivities.size
                den += total
            }
        }
        logger.debug { "nom=$nom den=$den" }
        return nom / den
    }
}