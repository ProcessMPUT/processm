package processm.conformance.measures.precision

import processm.conformance.measures.Measure
import processm.conformance.models.alignments.Alignment
import processm.conformance.models.alignments.CompositeAligner
import processm.core.log.hierarchical.Log
import processm.core.models.commons.Activity
import processm.core.models.commons.ProcessModel
import processm.helpers.Trie
import processm.logging.debug
import processm.logging.logger

/**
 * A generic implementation of precision. It requires overloading either variant of [availableActivities] in order to function correctly.
 * [invoke] expects either a sequence of [Alignment], or a Log - in the latter case, an alignment will be computed using some aligner,
 * so it is not efficient if to pass [Log] if you already have alignments (e.g., because you compute fitness at the same time)
 */
abstract class AbstractPrecision(open val model: ProcessModel) : Measure<Any, Double> {

    companion object {
        private val logger = logger()
    }

    /**
     * An auxiliary class to be used as values in a trie
     *
     * [total] Number of occurrences of the prefix represented by a given trie node
     * [available] The set of activities available for execution in the prefix represented by the node
     */
    data class PrecisionData(var total: Int, var available: Int, var availableAndChild: Int)

    /**
     * Complete [PrecisionData.available] for [prefixes] and each of its descendants
     *
     * It is implemented in terms of [availableActivities] for a single prefix, but whenever it makes sense this function
     * should be overriden, and then the other [availableActivities] may be left unimplemented
     */
    open fun availableActivities(prefixes: Trie<Activity, PrecisionData>) {
        prefixes.forEach { (prefix, trie) ->
            val available = availableActivities(prefix)
            trie.value.available = available.size
            trie.value.availableAndChild = trie.children.keys.count(available::contains)
        }
    }

    abstract fun availableActivities(prefix: List<Activity>): Set<Activity>

    /**
     * Extract model moves for each alignment
     */
    open fun translate(alignments: Sequence<Alignment>): Sequence<List<Activity>> =
        alignments.map { alignment -> alignment.steps.mapNotNull { if(it.modelMove?.isSilent == false) it.modelMove else null} }

    /**
     * For [artifact] being a sequence of [Alignment]s, compute and return precision.
     * For [artifact] being a [Log], compute the [Alignment]s, and then compute and return precision.
     * Otherwise, throw [IllegalArgumentException]
     */
    override fun invoke(artifact: Any): Double =
        when (artifact) {
            is Sequence<*> -> this(artifact)
            is Log -> this(artifact)
            else -> throw IllegalArgumentException("Artifact must be either Sequence<Alignment> or Log")
        }

    /**
     * Compute the [Alignment]s for the given log, and then compute and return precision.
     */
    open operator fun invoke(artifact: Log): Double =
        this(CompositeAligner(model).align(artifact))

    /**
     * Compute and return precision, as described in [1]. Whether the returned value is exact depends on both the quality of [Alignment]s, and the quality of [availableActivities]
     *
     * [1] van der Aalst, W., Adriansyah, A. and van Dongen, B. (2012), Replaying history on process models for conformance
     * checking and performance analysis. WIREs Data Mining Knowl Discov, 2: 182-192. https://doi.org/10.1002/widm.1045
     *
     * In the default implementation [artifact] is traversed exactly once and an overriding function should comply
     */
    open operator fun invoke(artifact: Sequence<Alignment>): Double {
        val observed = Trie<Activity, PrecisionData> { PrecisionData(0, 0, 0) }
        for (trace in translate(artifact)) {
            var current = observed
            for (activity in trace) {
                // incrementing total is first on purpose - we update the counter for an empty prefix, but we don't update the counter for the prefix = trace
                current.value.total += 1
                current = current.getOrPut(activity)
            }
        }
        availableActivities(observed)
        var nom = 0.0
        var den = 0
        for ((prefix, trie) in observed) {
            val (total, available, availableAndChild) = trie.value
            logger.debug { "$total $prefix $availableAndChild/$available" }
            if (total > 0 && available > 0) {
                nom += total * availableAndChild.toDouble() / available
                den += total
            }
        }
        logger.debug { "nom=$nom den=$den" }
        return nom / den
    }
}
