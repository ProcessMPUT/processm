package processm.conformance.models.antialignments

import processm.conformance.models.DeviationType
import processm.conformance.models.alignments.Aligner
import processm.conformance.models.alignments.Alignment
import processm.conformance.models.alignments.PenaltyFunction
import processm.conformance.models.alignments.Step
import processm.core.log.Event
import processm.core.log.hierarchical.Trace
import processm.core.models.commons.Activity
import processm.helpers.asList
import java.util.*
import kotlin.math.min

internal class NeedlemanWunschAligner(
    override val model: ReplayModel,
    override val penalty: PenaltyFunction = PenaltyFunction()
) : Aligner {

    init {
        assert(penalty.synchronousMove == 0)
        assert(penalty.silentMove == 0)
    }


    private var F: Array<IntArray> = Array(0) { IntArray(0) }

    // TODO BigInts and bits?
    private var same: Array<BooleanArray> = Array(0) { BooleanArray(0) }

    private fun fillSame(a: List<Activity>, b: List<Event>) {
        if (a.size + 1 > same.size || (same.isNotEmpty() && b.size + 1 > same[0].size)) {
            same = Array(a.size + 1) { BooleanArray(b.size + 1) }
        }
        for (i in 1..a.size)
            for (j in 1..b.size)
                same[i][j] = a[i - 1].name == b[j - 1].conceptName
    }

    private fun computeScores(a: List<Activity>, b: List<Event>) {
        if (a.size + 1 > F.size || (F.isNotEmpty() && b.size + 1 > F[0].size)) {
            F = Array(a.size + 1) { IntArray(b.size + 1) }
        }
        for (i in 0..a.size)
            F[i][0] = penalty.logMove * i
        for (j in 0..b.size)
            F[0][j] = penalty.modelMove * j
        for (i in 1..a.size)
            for (j in 1..b.size) {
                val del = F[i - 1][j] + penalty.logMove
                val ins = F[i][j - 1] + penalty.modelMove
                F[i][j] = min(ins, del)
                if (same[i][j])
                    F[i][j] = min(F[i][j], F[i - 1][j - 1])
            }
    }

    private fun backtrack(a: List<Activity>, b: List<Event>): LinkedList<Step> {
        val steps = LinkedList<Step>()
        var i = a.size
        var j = b.size
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && F[i][j] == F[i - 1][j - 1] && same[i][j]) {
                steps.add(0, Step(modelMove = a[i - 1], logMove = b[j - 1], type = DeviationType.None))
                i--
                j--
            } else if (i > 0 && F[i][j] == F[i - 1][j] + penalty.logMove) {
                steps.add(0, Step(modelMove = a[i - 1], logMove = null, type = DeviationType.LogDeviation))
                i--
            } else {
                assert(F[i][j] == F[i][j - 1] + penalty.modelMove)
                steps.add(0, Step(modelMove = null, logMove = b[j - 1], type = DeviationType.ModelDeviation))
                j--
            }
        }
        assert(i == 0 && j == 0)
        return steps
    }

    override fun align(trace: Trace, costUpperBound: Int): Alignment? {
        val events = trace.events.toList()
        val nonSilent = model.trace.filter { !it.isSilent }
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
            for (a in model.trace) {
                if (a.isSilent) {
                    i.add(Step(modelMove = a, logMove = null, type = DeviationType.ModelDeviation))
                } else {
                    var step = i.next()
                    while (step.modelMove === null && i.hasNext()) {
                        step = i.next()
                    }
                    assert(step.modelMove === a)
                }
            }
        }
        assert(steps.mapNotNull { it.modelMove } == model.trace)
        return Alignment(steps, cost)
    }
}