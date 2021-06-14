package processm.conformance.measures

import processm.conformance.models.alignments.Aligner
import processm.conformance.models.alignments.Alignment
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace

/**
 * Computes fitness (as defined by the PM book) using the given [aligner]
 */
class Fitness(
    val aligner: Aligner
) : Measure<Log, Double> {


    internal val movem: Int by lazy {
        aligner.align(Trace(emptySequence())).cost
    }

    override fun invoke(artifact: Log) = invoke(artifact, aligner.align(artifact).toList())

    private fun computeEmptyModelAlignmentCost(log: Log) = log.traces.map { trace ->
        trace.events.count() * aligner.penalty.logMove
    }.toList()

    /**
     * Use given [alignments] to compute fitness instead of computing alignments from scratch.
     * In cases when there is no not-null alignment in [alignments] for a trace, the maximal cost (skip all trace + skip the shortest valid binding sequence in the model) is used instead.
     */
    operator fun invoke(log: Log, alignments: List<Alignment?>?): Double {
        val emptyModelAlignmentCost = computeEmptyModelAlignmentCost(log)
        val movel = emptyModelAlignmentCost.sum()
        var fcost = 0.0
        for ((i, _) in log.traces.withIndex()) {
            val alignment = if (alignments != null && i < alignments.size) alignments[i] else null
            fcost += alignment?.cost ?: (emptyModelAlignmentCost[i] + movem)
        }
        return 1.0 - fcost / (movel + emptyModelAlignmentCost.size * movem)
    }

}