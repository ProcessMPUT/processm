package processm.miners.heuristicminer.windowing

import org.apache.commons.lang3.math.Fraction
import processm.core.models.causalnet.Node
import processm.miners.heuristicminer.ActiveDependencies
import processm.miners.heuristicminer.NodeTrace

data class CompositeSearchState(
    val position: Int,
    val parent: Int,
    val joins: Set<ActiveDependencies>,
    val splits: Set<ActiveDependencies>,
    val havingSomeJoin: Set<Node>,
    val havingSomeSplit: Set<Node>,
    val allActivities: Set<Node>,
    val base: List<SearchState>,
    val traces: List<NodeTrace>
) : HasFeatures() {

    override val features: List<Fraction>

    init {
        val progress = (base zip traces).map { (s, t) -> -s.node / t.size.toDouble() }
        val greediness = base.fold(Fraction.ZERO){ prev, it -> prev -it.totalGreediness / it.solutionLength }
        features = listOf(Fraction.getFraction(countUniqueBindings(), 1), greediness)
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
