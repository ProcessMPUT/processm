package processm.conformance.measures

import processm.conformance.models.alignments.Aligner
import processm.conformance.models.alignments.Alignment
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace

class Fitness(
    val aligner: Aligner
) : Measure<Log, Double> {


    internal val movem: Int by lazy {
        aligner.align(Trace(emptySequence())).cost
    }

    override fun invoke(artifact: Log) = invoke(artifact, aligner.align(artifact).toList())

    operator fun invoke(log: Log, alignments: Collection<Alignment?>): Double {
        val emptyModelAlignmentCost = log.traces.map { trace ->
            trace.events.count() * aligner.penalty.logMove
        }.toList()
        val n = emptyModelAlignmentCost.size
        require(n == alignments.size)
        val movel = emptyModelAlignmentCost.sum()
        val fcost = (alignments zip emptyModelAlignmentCost)
            .sumBy { (trueAlignment, emptyAlignmentCost) ->
                trueAlignment?.cost ?: (emptyAlignmentCost + movem)
            }
        return 1.0 - fcost.toDouble() / (movel + n * movem)
    }

}