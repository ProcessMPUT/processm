package processm.core.models.causalnet

import processm.core.models.causalnet.mock.Event

class AlwaysFirstDecisionModel : AbstractDecisionModel() {
    override fun predictSplitProbability(
        currentNode: Node,
        availableBindings: Iterable<Split>,
        partialLog: Sequence<Event>
    ): Map<Split, Double> {
        return availableBindings.mapIndexed { index, split -> if (index == 0) split to 1.0 else split to 0.0 }.toMap()
    }

    override fun predictJoinProbability(
        targetNode: Node,
        availableBindings: Iterable<Join>,
        partialLog: Sequence<Event>
    ): Map<Join, Double> {
        return availableBindings.mapIndexed { index, join -> if (index == 0) join to 1.0 else join to 0.0 }.toMap()
    }
}