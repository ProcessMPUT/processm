package processm.conformance.models.antialignments

import processm.conformance.models.DeviationType
import processm.conformance.models.alignments.Aligner
import processm.conformance.models.alignments.Alignment
import processm.conformance.models.alignments.PenaltyFunction
import processm.conformance.models.alignments.Step
import processm.core.log.Event
import processm.core.log.hierarchical.Trace
import processm.core.models.commons.Activity
import java.util.*
import kotlin.math.min


internal class HirschbergAligner(
    override val model: ReplayModel,
    override val penalty: PenaltyFunction = PenaltyFunction()
) : Aligner {

    init {
        assert(penalty.synchronousMove == 0)
        assert(penalty.silentMove == 0)
    }

    internal data class PrettyActivity(val id: Int, val a: Activity)
    internal data class PrettyEvent(val id: Int, val e: Event)

    internal fun nwScore(x: List<PrettyActivity>, y: List<PrettyEvent>): IntArray {
        var s0 = IntArray(y.size + 1)
        var s1 = IntArray(y.size + 1)
        for (j in y.indices)
            s0[j + 1] = s0[j] + penalty.logMove
        for (i in x.indices) {
            s1[0] = s0[0] + penalty.modelMove
            for (j in y.indices) {
                if (x[i].id == y[j].id) {
                    // always commit to the synchronous move
                    s1[j + 1] = s0[j]
                } else {
                    val del = s0[j + 1] + penalty.modelMove
                    val ins = s1[j] + penalty.logMove
                    s1[j + 1] = min(ins, del)
                }
            }
            s0 = s1.also { s1 = s0 }
            assert(s0 !== s1)
        }
        return s0
    }

    internal fun hirschberg(target: MutableList<Step>, x: List<PrettyActivity>, y: List<PrettyEvent>) {
        if (x.isEmpty()) {
            y.mapTo(target) { e -> Step(modelMove = null, logMove = e.e, type = DeviationType.LogDeviation) }
        } else if (y.isEmpty()) {
            x.mapTo(target) { a -> Step(modelMove = a.a, logMove = null, type = DeviationType.ModelDeviation) }
        } else if (x.size == 1) {
            // Wiki says I am supposed to call NeedlemanWunsch here, but I think we have simple enough case?
            val a = x.single()
            assert(y.isNotEmpty())
            var hit = false
            for (e in y)
                if (!hit && e.id == a.id) {
                    hit = true
                    target.add(Step(modelMove = a.a, logMove = e.e, type = DeviationType.None))
                } else
                    target.add(Step(modelMove = null, logMove = e.e, type = DeviationType.LogDeviation))
            if (!hit)
                target.add(Step(modelMove = a.a, logMove = null, type = DeviationType.ModelDeviation))
        } else if (y.size == 1) {
            // Wiki says I am supposed to call NeedlemanWunsch here, but I think we have simple enough case?
            assert(x.isNotEmpty())
            val e = y.single()
            var hit = false
            for (a in x)
                if (!hit && e.id == a.id) {
                    hit = true
                    target.add(Step(modelMove = a.a, logMove = e.e, type = DeviationType.None))
                } else
                    target.add(Step(modelMove = a.a, logMove = null, type = DeviationType.ModelDeviation))
            if (!hit)
                target.add(Step(modelMove = null, logMove = e.e, type = DeviationType.LogDeviation))
        } else {
            assert(x.size > 1)
            assert(y.size > 1)
            val xmid = x.size / 2
            val scoreL = nwScore(x.subList(0, xmid), y)
            val scoreR = nwScore(x.subList(xmid + 1, x.size).asReversed(), y.asReversed())
            val ymid = y.indices.minBy { scoreL[it] + scoreR[scoreR.size - it - 1] }
            hirschberg(target, x.subList(0, xmid), y.subList(0, ymid))
            hirschberg(
                target,
                if (xmid < x.size) x.subList(xmid, x.size) else emptyList(),
                if (ymid < y.size) y.subList(ymid, y.size) else emptyList()
            )
        }
    }

    private val name2index = HashMap<String?, Int>()
    private fun getId(name: String?) = name2index.computeIfAbsent(name) { name2index.size }

    override fun align(trace: Trace, costUpperBound: Int): Alignment? {
        name2index.clear()
        val steps = LinkedList<Step>()
        val nonSilent = model.trace.mapNotNull { if (!it.isSilent) PrettyActivity(getId(it.name), it) else null }
        hirschberg(steps, nonSilent, trace.events.map { PrettyEvent(getId(it.conceptName), it) }.toList())
        val cost = steps.sumOf {
            when (it.type) {
                DeviationType.None -> 0
                DeviationType.LogDeviation -> penalty.logMove
                DeviationType.ModelDeviation -> penalty.modelMove
            }
        }
        if (cost > costUpperBound)
            return null
        if (nonSilent.size < model.trace.size) {
            assert(steps.mapNotNull { it.modelMove } == nonSilent.map { it.a }) { "${steps.mapNotNull { it.modelMove }} $nonSilent" }
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