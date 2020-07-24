package processm.miners.heuristicminer.windowing

import processm.core.models.causalnet.Node
import processm.miners.heuristicminer.ActiveDependencies

data class CompositeSearchState(
    val position: Int,
    val parent: Int,
    val joins: Set<ActiveDependencies>,
    val splits: Set<ActiveDependencies>,
    val havingSomeJoin: Set<Node>,
    val havingSomeSplit: Set<Node>,
    val allActivities: Set<Node>,
    val base: List<SearchState>
) : HasFeatures() {

    override val features: List<Double>

    init {
        val progress = base.map { -it.node / it.nodeTrace.size.toDouble() }
        val greediness = base.sumByDouble { -it.totalGreediness / it.solutionLength }
        features = listOf(countUniqueBindings().toDouble(), greediness)
    }

    private fun countUniqueBindings(): Int {
        /*
        val havingSomeJoin = joins.mapToSet { it.first().target }
        val havingSomeSplit = splits.mapToSet { it.first().source }
        val remainingForProduction = HashSet<Node>()
        val remainingForConsumption = HashSet<Node>()
        for (part in base) {
            remainingForConsumption.addAll(
                part.nodeTrace.subList(
                    if (part.produce) part.node + 1 else part.node,
                    part.nodeTrace.size
                )
            )
            remainingForProduction.addAll(part.nodeTrace.subList(part.node, part.nodeTrace.size))
        }
         */
        //logger.trace{"joins=$joins splits=$splits"}
        /// -1 because start/end has only one binding
        return joins.size + splits.size + (allActivities.size - havingSomeJoin.size - 1) + (allActivities.size - havingSomeSplit.size - 1)
    }

}
