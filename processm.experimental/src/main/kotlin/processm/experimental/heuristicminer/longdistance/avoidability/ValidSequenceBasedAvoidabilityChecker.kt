package processm.experimental.heuristicminer.longdistance.avoidability

import processm.core.helpers.SequenceWithMemory
import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.Node
import processm.core.verifiers.CausalNetVerifier
import processm.core.verifiers.causalnet.CausalNetSequence

/**
 * An [AvoidabilityChecker] using valid sequences provided by [CausalNetVerifier] to verify whether a given dependency is avoidable.
 */
class ValidSequenceBasedAvoidabilityChecker :
    AvoidabilityChecker {

    private var seqs: SequenceWithMemory<CausalNetSequence>? = null

    override fun setContext(model: CausalNet) {
        seqs = CausalNetVerifier().verify(model).validLoopFreeSequences
    }

    /**
     * Returns true if there is no predecessor or both predecessor and successor are present
     */
    private fun CausalNetSequence.fulfills(dep: Pair<Set<Node>, Set<Node>>): Boolean {
        val indices = dep.first.map { expected -> indexOfFirst { ab -> ab.a == expected } }
        if (indices.any { it == -1 })
            return true
        val first = indices.max()!!
        return this.subList(first + 1, this.size).map { it.a }.containsAll(dep.second)
    }


    override fun invoke(dependency: Pair<Set<Node>, Set<Node>>): Boolean {
        val result = seqs?.any { seq -> !seq.fulfills(dependency) }
        checkNotNull(result)
        return result
    }
}