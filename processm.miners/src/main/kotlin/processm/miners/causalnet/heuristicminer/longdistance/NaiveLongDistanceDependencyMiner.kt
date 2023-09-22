package processm.miners.causalnet.heuristicminer.longdistance

import processm.core.helpers.Counter
import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.Dependency
import processm.core.models.causalnet.Node
import processm.miners.causalnet.heuristicminer.longdistance.avoidability.AvoidabilityChecker
import processm.miners.causalnet.heuristicminer.longdistance.avoidability.ValidSequenceBasedAvoidabilityChecker

/**
 * A very simple approach for long-distance dependenyc mining, boiling down to mining sequential rules
 * with a single premise and a single conclusions. Seems to be sound (i.e., all mined dependencies are real) as
 * long as [minLongTermDependency] is approximately 1, but vastly incomplete
 */
class NaiveLongDistanceDependencyMiner(
    val minLongTermDependency: Double = 0.9999,
    val avoidabilityChecker: AvoidabilityChecker = ValidSequenceBasedAvoidabilityChecker()
) : LongDistanceDependencyMiner {
    private val predecessorCtr = Counter<Node>()
    private val pairsCtr = Counter<Dependency>()

    override fun processTrace(trace: List<Node>) {
        predecessorCtr.inc(trace)
        pairsCtr.inc(trace.mapIndexed { index, pred ->
            trace.subList(index + 1, trace.size).map { succ -> Dependency(pred, succ) }
        }.flatten())
    }

    override fun mine(model: CausalNet): Map<Dependency, Double> {
        val known = (model.outgoing + model.incoming)
            .values
            .flatten()
            .toSet()
        avoidabilityChecker.setContext(model)
        val result = HashMap<Dependency, Double>()
        for ((dep, ctr) in pairsCtr) {
            if (!known.contains(dep)) {
                val measure = ctr.toDouble() / predecessorCtr.getValue(dep.source)
                if (measure >= minLongTermDependency &&
                    !(dep.source == model.start && dep.target == model.end) &&
                    avoidabilityChecker.invoke(setOf(dep.source) to setOf(dep.target))
                )
                    result[dep] = measure
            }
        }
        return result
    }
}