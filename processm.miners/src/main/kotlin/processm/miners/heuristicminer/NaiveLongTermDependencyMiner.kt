package processm.miners.heuristicminer

import processm.core.models.causalnet.Model
import processm.core.models.causalnet.Node
import processm.miners.heuristicminer.avoidability.*

class NaiveLongTermDependencyMiner(
    val minLongTermDependency: Double = 0.9999,
    val avoidabilityChecker: AvoidabilityChecker = ValidSequenceBasedAvoidabilityChecker()
) : LongTermDependencyMiner {
    private val predecessorCtr = Counter<Node>()
    private val pairsCtr = Counter<Pair<Node, Node>>()

    override fun processTrace(trace: List<Node>) {
        predecessorCtr.inc(trace)
        pairsCtr.inc(trace.mapIndexed { index, pred ->
            trace.subList(index + 1, trace.size).map { succ -> pred to succ }
        }.flatten())
    }

    override fun mine(model: Model): Collection<Pair<Node, Node>> {
        val known = (model.outgoing + model.incoming)
            .values
            .flatten()
            .map { d -> d.source to d.target }
            .toSet()
        avoidabilityChecker.setContext(model)
        return pairsCtr
            .filter { (dep, ctr) -> !known.contains(dep) }
            .map { (dep, ctr) -> dep to ctr.toDouble() / predecessorCtr.getValue(dep.first) }
            .filter { (dep, ctr) -> ctr >= minLongTermDependency }
            .map { (dep, ctr) -> dep }
            .filter { dep -> !(dep.first == model.start && dep.second == model.end) }
            .filter { dep -> avoidabilityChecker.invoke(dep) }
    }
}