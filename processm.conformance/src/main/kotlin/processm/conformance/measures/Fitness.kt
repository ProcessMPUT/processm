package processm.conformance.measures

import processm.conformance.models.alignments.Aligner
import processm.conformance.models.alignments.Alignment
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace

open class Fitness(
    open val aligner: Aligner
) : Measure<Log, Double> {


    internal val movem: Int by lazy {
        aligner.align(Trace(emptySequence())).cost
    }

    override fun invoke(artifact: Log) = invoke(artifact, aligner.align(artifact).toList())

    protected fun computeEmptyModelAlignmentCost(log: Log) = log.traces.map { trace ->
        trace.events.count() * aligner.penalty.logMove
    }.toList()

    protected open fun alignmentCost(trace: Trace, alignment: Alignment?): Double? = alignment?.cost?.toDouble()

    open operator fun invoke(log: Log, alignments: List<Alignment?>?): Double {
        val emptyModelAlignmentCost = computeEmptyModelAlignmentCost(log)
        val movel = emptyModelAlignmentCost.sum()
        var fcost = 0.0
        for ((i, trace) in log.traces.withIndex()) {
            val alignment = if (alignments != null && i < alignments.size) alignments[i] else null
            fcost += alignmentCost(trace, alignment) ?: (emptyModelAlignmentCost[i] + movem).toDouble()
        }
        return 1.0 - fcost / (movel + emptyModelAlignmentCost.size * movem)
    }

}