package processm.miners.heuristicminer.bindingproviders

import processm.core.helpers.allSubsets
import processm.core.helpers.materializedAllSubsets
import processm.core.models.causalnet.Dependency
import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.Node

abstract class AbstractBindingProvider : BindingProvider {

    /**
     * Generate sets of dependencies that can be consumed (i.e., removed from state) according to [model], given that
     * the target of these dependencies must be [currentNode] and they all must be present in [available]
     */
    protected fun consumeCandidates(
            model: CausalNet,
            currentNode: Node,
            available: Set<Dependency>
    ): Sequence<Collection<Dependency>> {
        val consumable =
            model.incoming.getOrDefault(currentNode, setOf())
        val knownJoins = model.joins[currentNode]
        return if (knownJoins.isNullOrEmpty()) {
            if (consumable.isNotEmpty())
                consumable.intersect(available).allSubsets().filter { it.isNotEmpty() }
            else
                sequenceOf(emptySet<Dependency>())
        } else {
            knownJoins.map { join -> join.dependencies }.filter { available.containsAll(it) }.asSequence()
        }
    }

    /**
     * Generate sets of dependencies that can be produced (i.e., added to state) according to [model], given that
     * the source of these dependencies must be [currentNode] and their targets must be present in [available]
     */
    protected fun produceCandidates(
            model: CausalNet,
            currentNode: Node,
            available: Set<Node>
    ): List<Collection<Dependency>> {
        val producible =
            model.outgoing.getOrDefault(currentNode, setOf())
        val knownSplits = model.splits[currentNode]
        return if (knownSplits.isNullOrEmpty()) {
            if (producible.isNotEmpty())
                producible.filter { it.target in available }.materializedAllSubsets(true)
            else
                listOf(setOf<Dependency>())
        } else {
            knownSplits.filter { available.containsAll(it.targets) }.map { split -> split.dependencies }
        }
    }
}