package processm.miners.heuristicminer

import processm.core.models.causalnet.Model
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.verifier.CausalNetSequence


/**
 * Returns true if there is no predecessor or both predecessor and successor are present
 */
internal fun CausalNetSequence.fulfills(dep: Pair<Node, Node>): Boolean {
    println("${dep.first.activity}->${dep.second.activity} in? " + this.map { ab -> ab.a.activity })
    val first = this.indexOfFirst { ab -> ab.a == dep.first }
    if (first == -1)
        return true
    return this.subList(first + 1, this.size).find { ab -> ab.a == dep.second } != null
}

interface LongTermDependencyMiner {
    fun processTrace(trace: Trace)
    fun mine(currentModel: Model): Collection<Pair<Node, Node>>
}