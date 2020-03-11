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
    private fun CausalNetSequence.fulfills(dep: Pair<Node, Node>): Boolean {
        val first = this.indexOfFirst { ab -> ab.a == dep.first }
        if (first == -1)
            return true
        return this.subList(first + 1, this.size).any { ab -> ab.a == dep.second }
    }


    override fun invoke(dependency: Pair<Node, Node>): Boolean {
        val result = seqs?.any { seq -> !seq.fulfills(dependency) }
        if (result != null)
            return result
        else
            throw IllegalStateException()
    }
}