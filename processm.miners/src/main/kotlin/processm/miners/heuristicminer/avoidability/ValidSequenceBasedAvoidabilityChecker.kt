package processm.miners.heuristicminer.avoidability

import processm.core.helpers.SequenceWithMemory
import processm.core.helpers.withMemory
import processm.core.models.causalnet.Model
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.verifier.CausalNetSequence
import processm.core.models.causalnet.verifier.Verifier

class ValidSequenceBasedAvoidabilityChecker :
    AvoidabilityChecker {

    private var seqs: SequenceWithMemory<CausalNetSequence>? = null

    override fun setContext(model: Model) {
        seqs = Verifier(model).validLoopFreeSequences
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
        if (result != null)
            return result
        else
            throw IllegalStateException()
    }
}