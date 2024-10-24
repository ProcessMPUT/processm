package processm.conformance.models.antialignments

import processm.conformance.models.DeviationType
import processm.conformance.models.alignments.Aligner
import processm.conformance.models.alignments.Alignment
import processm.conformance.models.alignments.PenaltyFunction
import processm.conformance.models.alignments.Step
import processm.core.log.Event
import processm.core.log.hierarchical.Trace
import processm.core.models.commons.Activity
import kotlin.math.min


internal class HirschbergAligner(
    override val model: ReplayModel,
    override val penalty: PenaltyFunction = PenaltyFunction()
) : Aligner {

    private val Activity.deletionCost: Int
        get() = if (isSilent) penalty.silentMove else penalty.modelMove

    private val Event.insertionCost: Int
        get() = penalty.logMove

    internal fun nwScore(x: List<Activity>, y: List<Event>): IntArray {
        var s0 = IntArray(y.size + 1)
        var s1 = IntArray(y.size + 1)
        for (j in y.indices)
            s0[j + 1] = s0[j] + y[j].insertionCost
        for (i in x.indices) {
            s1[0] = s0[0] + x[i].deletionCost
            for (j in y.indices) {
                val del = s0[j + 1] + x[i].deletionCost
                val ins = s1[j] + y[j].insertionCost
                s1[j + 1] = min(ins, del)
                if (x[i].name == y[j].conceptName && !x[i].isSilent)
                    s1[j + 1] = min(s1[j + 1], s0[j] + penalty.synchronousMove)
            }
            s0 = s1.also { s1 = s0 }
            assert(s0 !== s1)
        }
        return s0
    }

    internal fun hirschberg(x: List<Activity>, y: List<Event>): List<Step> {
        if (x.isEmpty()) {
            return y.map { e -> Step(modelMove = null, logMove = e, type = DeviationType.LogDeviation) }
        } else if (y.isEmpty()) {
            return x.map { a -> Step(modelMove = a, logMove = null, type = DeviationType.ModelDeviation) }
        } else if (x.size == 1) {
            // Wiki says I am supposed to call NeedlemanWunsch here, but I think we have simple enough case?
            val a = x.single()
            if (a.isSilent) {
                return ArrayList<Step>().apply {
                    add(Step(modelMove = a, logMove = null, type = DeviationType.ModelDeviation))
                    y.mapTo(this) { e -> Step(modelMove = null, logMove = e, type = DeviationType.LogDeviation) }
                }
            } else {
                assert(y.isNotEmpty())
                return ArrayList<Step>().apply {
                    var hit = false
                    for (e in y)
                        if (!hit && e.conceptName == a.name) {
                            hit = true
                            add(Step(modelMove = a, logMove = e, type = DeviationType.None))
                        } else
                            add(Step(modelMove = null, logMove = e, type = DeviationType.LogDeviation))
                }
            }
        } else if (y.size == 1) {
            // Wiki says I am supposed to call NeedlemanWunsch here, but I think we have simple enough case?
            assert(x.isNotEmpty())
            return ArrayList<Step>().apply {
                val e = y.single()
                var hit = false
                for (a in x)
                    if (!hit && e.conceptName == a.name) {
                        hit = true
                        add(Step(modelMove = a, logMove = e, type = DeviationType.None))
                    } else
                        add(Step(modelMove = a, logMove = null, type = DeviationType.ModelDeviation))
            }
        } else {
            assert(x.size > 1)
            assert(y.size > 1)
            val xmid = x.size / 2
            val scoreL = nwScore(x.subList(0, xmid), y)
            val scoreR = nwScore(x.subList(xmid + 1, x.size).reversed(), y.reversed())
            val ymid = y.indices.minBy { scoreL[it] + scoreR[scoreR.size - it - 1] }
            return hirschberg(x.subList(0, xmid), y.subList(0, ymid)) + hirschberg(
                if (xmid < x.size) x.subList(xmid, x.size) else emptyList(),
                if (ymid < y.size) y.subList(ymid, y.size) else emptyList()
            )
        }
    }

    internal fun lev(trace: Trace): Int {
        val events = trace.events.toList()
        val activities = model.trace
        val m = events.size + 1
        val n = activities.size + 1
        val d = Array(m) { IntArray(n) }
        for (i in 1 until m)
            d[i][0] = i
        for (j in 1 until n)
            d[0][j] = j
        for (j in 1 until n)
            for (i in 1 until m) {
                d[i][j] = min(d[i - 1][j] + 1, d[i][j - 1] + (if (activities[j - 1].isSilent) 0 else 1))//TODO costs
                if (events[i - 1].conceptName == activities[j - 1].name)
                    d[i][j] = min(d[i][j], d[i - 1][j - 1] + 0) //TODO costs
            }
        for (j in 0 until n) {
            for (i in 0 until m) {
                print(d[i][j])
            }
            println()
        }
        return d[m - 1][n - 1]
    }

    private val Step.cost: Int
        get() = when (type) {
            DeviationType.None -> 0
            DeviationType.LogDeviation -> logMove!!.insertionCost
            DeviationType.ModelDeviation -> modelMove!!.deletionCost
        }

    override fun align(trace: Trace, costUpperBound: Int): Alignment? {
        val steps = hirschberg(model.trace, trace.events.toList())
        return Alignment(steps, steps.sumOf { it.cost })
    }
}