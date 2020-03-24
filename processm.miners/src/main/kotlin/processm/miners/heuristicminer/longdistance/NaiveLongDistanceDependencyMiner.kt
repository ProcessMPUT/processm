package processm.miners.heuristicminer.longdistance

import processm.core.helpers.Counter
import processm.core.models.causalnet.Model
import processm.core.models.causalnet.Node
import processm.miners.heuristicminer.avoidability.*

/**
 * A very simple approach for long-distance dependenyc mining, boiling down to mining sequential rules
 * with a single premise and a single conclusions. Seems to be sound (i.e., all mined dependencies are real) as
 * long as [minLongTermDependency] is approximately `, but vastly incomplete
 */
class NaiveLongDistanceDependencyMiner(
    val minLongTermDependency: Double = 0.9999,
    val avoidabilityChecker: AvoidabilityChecker = ValidSequenceBasedAvoidabilityChecker()
) : LongDistanceDependencyMiner {
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
            .filter { dep -> avoidabilityChecker.invoke(setOf(dep.first) to setOf(dep.second)) }
    }
}