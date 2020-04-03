package processm.miners.heuristicminer.bindingproviders

import processm.core.helpers.allSubsets
import processm.core.helpers.materializedAllSubsets
import processm.core.models.causalnet.Dependency
import processm.core.models.causalnet.Model
import processm.core.models.causalnet.Node

abstract class AbstractBindingProvider : BindingProvider {
    protected fun consumeCandidates(
        model: Model,
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

    protected fun produceCandidates(
        model: Model,
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