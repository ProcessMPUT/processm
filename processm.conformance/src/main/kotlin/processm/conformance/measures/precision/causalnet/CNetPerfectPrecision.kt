package processm.conformance.measures.precision.causalnet

import processm.core.helpers.Trie
import processm.core.logging.debug
import processm.core.logging.logger
import processm.core.models.causalnet.CausalNet
import processm.core.models.commons.Activity
import processm.core.verifiers.causalnet.CausalNetVerifierImpl

/**
 * An exact implementation of precision for [CausalNet]s, possibly computationally very expensive
 */
class CNetPerfectPrecision(model: CausalNet) : CNetAbstractPrecision(model) {

    companion object {
        private val logger = logger()
    }

    /**
     * Instead of generating all the valid sequences (possibly infinitely many), we generate only such valid sequences that can provide some new information in the context of [prefixes]
     * In particular this means that calling [availableActivities] second time on the same object will lead to generating no valid sequences whatsoever
     *
     * An incomplete valid sequence (i.e., a prefix which potentially may be extended to a valid sequence) can provide new information if either:
     * 1. Is a prefix present in [prefixes]
     * 2. Starts with a prefix present in [prefixes] and the first position after the prefix is not registered as available for the prefix
     */
    override fun availableActivities(prefixes: Trie<Activity, PrecisionData>) {
        val observed = Trie<Activity, HashSet<Activity>> { HashSet() }
        for (entry in prefixes)
            observed.getOrPut(entry.prefix)
        val verifier = CausalNetVerifierImpl(model)
        // valid sequences runs BFS and there is only a finite number of possible successors, so I think this terminates
        val seqs = verifier.computeSetOfValidSequences(false) { seq, _ ->
            val activities = seq.mapNotNull { if (!it.a.isSilent) it.a else null }
            // Either activities as a whole is in the prefix trie or the first position not present in the prefix trie is still not registered as a possible continuation of the prefix
            // Keep in mind this function is executed not immediately, but during the iteration over the returned sequence, i.e, in the for loop below
            logger.debug { "intermediate seq=$activities" }
            var current = observed
            for (activity in activities) {
                current = current.getOrNull(activity)
                    ?: return@computeSetOfValidSequences activity !in current.value
            }
            return@computeSetOfValidSequences true
        }
        for (seq in seqs) {
            val activities = seq.mapNotNull { if (!it.a.isSilent) it.a else null }
            logger.debug { "seq=$activities" }
            var current = observed
            for (activity in activities) {
                current.value.add(activity)
                current = current.getOrNull(activity) ?: break
            }
        }
        for (entry in prefixes) {
            val o = observed.getOrNull(entry.prefix)?.value
            checkNotNull(o)
            entry.trie.value.available = o.size
            entry.trie.value.availableAndChild = o.count(entry.trie.children::containsKey)
        }
    }

    override fun availableActivities(prefix: List<Activity>): Set<Activity> {
        throw NotImplementedError("This function is not implemented on purpose, as CNetPerfectPrecision reimplements availableActivities working directly on a trie")
    }


}