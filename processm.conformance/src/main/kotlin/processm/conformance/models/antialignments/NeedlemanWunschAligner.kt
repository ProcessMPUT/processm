package processm.conformance.models.antialignments

import processm.conformance.models.DeviationType
import processm.conformance.models.alignments.Aligner
import processm.conformance.models.alignments.Alignment
import processm.conformance.models.alignments.PenaltyFunction
import processm.conformance.models.alignments.Step
import processm.core.log.Event
import processm.core.log.hierarchical.Trace
import processm.core.models.commons.Activity
import processm.core.models.commons.ProcessModelState
import java.util.*
import kotlin.math.min

/**
 * An aligner designed to align with a sequential model. It employs the Needleman–Wunsch algorithm (1) for aligning two sequences.
 * In theory, [HirschbergAligner] should be more efficient, however, that does not seem to be the case here.
 *
 * (1) Needleman, Saul B. & Wunsch, Christian D. (1970). "A general method applicable to the search for similarities in
 * the amino acid sequence of two proteins". Journal of Molecular Biology. 48 (3): 443–53. doi:10.1016/0022-2836(70)90057-4
 */
internal class NeedlemanWunschAligner(
    override val model: ReplayModel,
    override val penalty: PenaltyFunction = PenaltyFunction()
) : Aligner {

    init {
        assert(penalty.synchronousMove == 0)
        assert(penalty.silentMove == 0)
    }

    private val logMoveCost = penalty.logMove
    private val modelMoveCost = penalty.modelMove

    private var F: IntArray = IntArray(0)
    private var n: Int = 0


    private var same = BooleanArray(0)

    private fun fillSame(a: List<Activity>, b: List<Event>) {
        if (same.size < (a.size + 1) * (b.size + 1))
            same = BooleanArray((a.size + 1) * (b.size + 1))
        for (i in 1..a.size) {
            val aname = a[i - 1].name
            for (j in 1..b.size)
                same[i * n + j] = aname == b[j - 1].conceptName
        }
    }

    private fun computeScores(a: List<Activity>, b: List<Event>) {
        if (F.size < (a.size + 1) * (b.size + 1))
            F = IntArray((a.size + 1) * (b.size + 1))
        for (i in 0..a.size)
            F[i * n] = logMoveCost * i
        for (j in 0..b.size)
            F[j] = modelMoveCost * j
        for (i in 1..a.size) {
            val k = i * n
            for (j in 1..b.size) {
                val l = k + j
                val del = F[l - n] + logMoveCost
                val ins = F[l - 1] + modelMoveCost
                val v = min(ins, del)
                F[l] = v
                if (same[l]) {
                    val v2 = F[l - n - 1]
                    if (v2 < v)
                        F[l] = v2
                }
            }
        }
    }

    private fun backtrack(a: List<Activity>, b: List<Event>): LinkedList<Step> {
        val steps = LinkedList<Step>()
        var i = a.size
        var j = b.size
        var modelState = ReplayModelState(i)
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && F[i * n + j] == F[(i - 1) * n + j - 1] && same[i * n + j]) {
                steps.add(
                    0,
                    Step(modelMove = a[i - 1], logMove = b[j - 1], type = DeviationType.None, modelState = modelState)
                )
                i--
                j--
                modelState = ReplayModelState(i)
            } else if (i > 0 && F[i * n + j] == F[(i - 1) * n + j] + penalty.logMove) {
                steps.add(
                    0,
                    Step(
                        modelMove = a[i - 1],
                        logMove = null,
                        type = DeviationType.LogDeviation,
                        modelState = modelState
                    )
                )
                i--
                modelState = ReplayModelState(i)
            } else {
                assert(F[i * n + j] == F[i * n + j - 1] + penalty.modelMove)
                steps.add(
                    0,
                    Step(
                        modelMove = null,
                        logMove = b[j - 1],
                        type = DeviationType.ModelDeviation,
                        modelState = modelState
                    )
                )
                j--
            }
        }
        assert(i == 0 && j == 0)
        return steps
    }

    override fun align(trace: Trace, costUpperBound: Int): Alignment? {
        val events = trace.events.toList()
        val nonSilent = model.trace.filter { !it.isSilent }
        n = events.size
        fillSame(nonSilent, events)
        computeScores(nonSilent, events)
        val steps = backtrack(nonSilent, events)
        val cost = steps.sumOf {
            when (it.type) {
                DeviationType.None -> 0
                DeviationType.LogDeviation -> penalty.logMove
                DeviationType.ModelDeviation -> penalty.modelMove
            }
        }
        if (nonSilent.size < model.trace.size) {
            assert(steps.mapNotNull { it.modelMove } == nonSilent) { "${steps.mapNotNull { it.modelMove }} $nonSilent" }
            val i = steps.listIterator()
            var modelState: ProcessModelState? = null
            for (a in model.trace) {
                if (a.isSilent) {
                    i.add(
                        Step(
                            modelMove = a,
                            logMove = null,
                            type = DeviationType.ModelDeviation,
                            modelState = modelState
                        )
                    )
                } else {
                    var step = i.next()
                    while (step.modelMove === null && i.hasNext()) {
                        step = i.next()
                    }
                    modelState = step.modelState
                    assert(step.modelMove === a)
                }
            }
        }
        assert(steps.mapNotNull { it.modelMove } == model.trace)
        return Alignment(steps, cost)
    }
}