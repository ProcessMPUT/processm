package processm.conformance.measures.precision.causalnet

import processm.conformance.models.alignments.AStar
import processm.conformance.models.alignments.Alignment
import processm.conformance.models.alignments.Step
import processm.core.log.hierarchical.Log
import processm.core.logging.logger
import processm.core.logging.trace
import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.DecoupledNodeExecution
import processm.core.models.causalnet.Node

internal abstract class CNetPrecisionAux(
    protected val log: Log,
    protected val model: CausalNet
) {

    companion object {
        private val logger = logger()
    }

    //TODO consider making this a parameter to enable the user to decide which aligner use and possibly use the same alignment for various purposes
    internal val optimalAlignment: List<Alignment?> by lazy {
        val result = AStar(model).align(log).toList()
        if (logger.isTraceEnabled) {
            logger.trace("Alignments")
            result.forEach { logger.trace("$it") }
            logger.trace("End alignments")
        }
        return@lazy result
    }


    private val prefix2Step: Map<List<Node>, List<Step>> by lazy {
        val result = HashMap<List<Node>, ArrayList<Step>>()
        for (alignment in optimalAlignment) {
            if (alignment != null) {
                val prefix = ArrayList<Node>()
                for (step in alignment.steps) {
                    result.computeIfAbsent(ArrayList(prefix)) { ArrayList() }.add(step)
                    val move = step.modelMove
                    if (move != null) {
                        check(move is DecoupledNodeExecution)
                        prefix.add(move.activity)
                    }
                }
            }
        }
        return@lazy result
    }

    private val prefix2Possible: Map<List<Node>, Set<Node>> by lazy {
        assert(emptyList() in prefix2Step)
        val result = possibleNext(prefix2Step.keys).toMutableMap()
        assert(emptyList() in result)
        if (logger.isTraceEnabled) {
            logger.trace("Possible next")
            result.forEach { logger.trace("$it") }
            logger.trace("End possible next")
        }
        return@lazy result
    }


    internal fun possibleNext(prefixes: Collection<List<Node>>): Map<List<Node>, Set<Node>> =
        prefixes.associateWith { prefix -> possibleNext(prefix) }

    internal abstract fun possibleNext(prefix: List<Node>): Set<Node>

    internal fun precisionHelper(prefix2Possible: Map<List<Node>, Set<Node>>): Double {
        var result = 0.0
        val prefix2Next = prefix2Step.mapValues {
            it.value.mapNotNullTo(HashSet()) { step ->
                (step.modelMove as DecoupledNodeExecution?)?.activity
            }
        }
        var ctr = 0
        for (alignment in optimalAlignment) {
            if (alignment != null) {
                val prefix = ArrayList<Node>()
                for (step in alignment.steps) {
                    val move = step.modelMove
                    if (move != null) {
                        val observed = prefix2Next.getValue(prefix)
                        val possible = prefix2Possible[prefix]
                        logger().trace { "observed=$observed possible=$possible" }
                        if (possible == null) {
                            logger.warn("Nothing is possible in $prefix")
                            continue
                        }
                        if (!possible.containsAll(observed)) {
                            logger.warn("For $prefix there are activities that were observed, but are not possible: ${observed - possible}")
                            observed.removeIf { it !in possible }
                        }
                        result += observed.size.toDouble() / possible.size.toDouble()
                        ctr += 1
                        check(move is DecoupledNodeExecution)
                        prefix.add(move.activity)
                    }
                }
            }
        }
        return result / ctr
    }

    val precision: Double by lazy {
        precisionHelper(prefix2Possible)
    }

}