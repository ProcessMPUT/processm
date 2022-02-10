package processm.conformance.measures.precision

import processm.conformance.measures.Measure
import processm.conformance.models.alignments.AStar
import processm.conformance.models.alignments.Alignment
import processm.core.helpers.Trie
import processm.core.log.hierarchical.Log
import processm.core.logging.debug
import processm.core.logging.logger
import processm.core.models.commons.Activity
import processm.core.models.commons.ProcessModel

/**
 * A generic implementation of precision. It requires overloading either variant of [availableActivities] in order to function correctly.
 * [invoke] expects either a sequence of [Alignment], or a Log - in the latter case, an alignment will be computed using some aligner,
 * so it is not efficient if to pass [Log] if you already have alignments (e.g., because you compute fitness at the same time)
 */
abstract class AbstractPrecision(open val model: ProcessModel) : Measure<Any, Double> {

    companion object {
        private val logger = logger()
    }

    data class PrecisionData(var total: Int, var available: Set<Activity>? = null)

    open fun availableActivities(prefixes: Trie<Activity, PrecisionData>) {
        prefixes.forEach { (prefix, trie) ->
            trie.value.available = availableActivities(prefix)
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
        val observed = Trie<Activity, PrecisionData> { PrecisionData(0) }
        for (trace in translate(artifact)) {
            var current = observed
            for (activity in trace) {
                // incrementing total is first on purpose - we update the counter for an empty prefix, but we don't update the counter for the prefix = trace
                current.value.total += 1
                current = current[activity]
            }
        }
        availableActivities(observed)
        var nom = 0.0
        var den = 0
        for ((prefix, trie) in observed) {
            val (total, availableActivities) = trie.value
            val children = trie.children.keys
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