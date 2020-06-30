package processm.experimental.performance

import com.google.common.collect.MinMaxPriorityQueue
import processm.core.helpers.Counter
import processm.core.helpers.HierarchicalIterable
import processm.core.helpers.mapToSet
import processm.core.log.Event
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.logging.logger
import processm.core.logging.trace
import processm.core.models.causalnet.*
import processm.core.models.commons.Activity
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet

class PerformanceAnalyzer(
    private val log: Log,
    private val model: CausalNet,
    private val distance: Distance = StandardDistance()
) {

    companion object {
        private val logger = logger()
    }

    private fun align(partialAlignment: Alignment, dec: DecoupledNodeExecution?, event: Event?): Alignment? {
        val newCost = partialAlignment.cost + distance(dec?.activity, event)
        if (newCost < distance.maxAcceptableDistance) {
            val nextState = CausalNetStateImpl(partialAlignment.state)
            if (dec != null)
                nextState.execute(dec.join, dec.split)
            val step = AlignmentStep(event, dec?.activity, partialAlignment.state)
            val nextAlignment = HierarchicalIterable(partialAlignment.alignment, step)
            var nextPosition = partialAlignment.tracePosition
            if (event != null)
                nextPosition += 1
            return Alignment(newCost, nextAlignment, nextState, nextPosition, partialAlignment.context)
        }
        return null
    }

    private class AlignmentComparator : Comparator<Alignment> {

        override fun compare(a: Alignment, b: Alignment): Int {
            for (i in a.features.indices) {
                val fa = a.features[i].value
                val fb = b.features[i].value
                val v = fa.compareTo(fb)
                if (v != 0)
                    return v
            }
            return 0
        }

    }

    private fun initialAlignment(events: List<Event>): List<Alignment> {
        val context = AlignmentContext(model, events, distance)
        return listOf(Alignment(0.0, emptyList<AlignmentStep>(), CausalNetStateImpl(), 0, context))
    }

    internal fun allFreePartialAlignments(events: List<Activity>): List<Alignment> {
        val result = ArrayList<Alignment>()
        val queue = ArrayDeque<Alignment>()
        queue.addAll(initialAlignment(emptyList<Event>()))
        while (queue.isNotEmpty()) {
            val partialAlignment = queue.poll()
            if (partialAlignment.tracePosition < events.size) {
                val event = events[partialAlignment.tracePosition]
                val decisions = model.available4(partialAlignment.state, event as Node)
                for (dec in decisions) {
                    assert (dec.activity == event)
                    val nextState = CausalNetStateImpl(partialAlignment.state)
                    for(dep in dec.join?.dependencies.orEmpty())
                        nextState.remove(dep)
                    nextState.addAll(dec.split?.dependencies.orEmpty())
//                    nextState.execute(dec.join, dec.split)
                    val step = AlignmentStep(null, dec.activity, partialAlignment.state)
                    val nextAlignment = HierarchicalIterable(partialAlignment.alignment, step)
                    val nextPosition = partialAlignment.tracePosition + 1
                    queue.add(Alignment(0.0, nextAlignment, nextState, nextPosition, partialAlignment.context))
                }
            } else
                result.add(partialAlignment)
        }
        return result
    }

    private fun modelReachedEnd(partialAlignment: Alignment): Boolean {
        return partialAlignment.state.isEmpty() && partialAlignment.alignment.any { it.activity != null }
    }

    private fun addMissingEnd(alignment: Alignment): Alignment {
        return alignment
    }

    private class CostCache : HashMap<AlignmentState, Double>() {
        override operator fun get(key: AlignmentState) = super.get(key) ?: Double.POSITIVE_INFINITY
    }

    /**
     * @return Alignment + number of visited states
     */
    internal fun computeOptimalAlignment(trace: Trace): Pair<Alignment, Int> {
        val bestCost = CostCache()
        val events = trace.events.toList()
        val queue = PriorityQueue(AlignmentComparator())
//        val queue = MinMaxPriorityQueue.orderedBy(AlignmentComparator()).maximumSize(100).create<Alignment>()
        queue.addAll(initialAlignment(events))
        var ctr = 0
        var skipCtr = 0
        while (queue.isNotEmpty()) {
            val partialAlignment = queue.poll()
            if (bestCost[partialAlignment.alignmentState] <= partialAlignment.cost) {
                skipCtr++
                continue
            }
            if (partialAlignment.state.isNotEmpty())
                bestCost[partialAlignment.alignmentState] = partialAlignment.cost
            ctr += 1
            logger.trace { "Queue size: ${queue.size}, cost: ${partialAlignment.cost}: " + partialAlignment.alignment.joinToString { "${it.event?.conceptName ?: '⊥'} -> ${it.activity ?: '⊥'}" } }
            val modelReachedEnd = modelReachedEnd(partialAlignment)
            val traceReachedEnd = partialAlignment.tracePosition >= events.size
            if (modelReachedEnd && traceReachedEnd) {
                logger.trace("Visited $ctr partial alignments, skipctr=$skipCtr")
                return addMissingEnd(partialAlignment) to ctr
            }
            // match is set to true if there is an alignment between possible decisions and current event
            // The idea is that one should not skip if it is possible to advance in log and model at the same time
            var match = false
            if (!modelReachedEnd) {
                val decisions = model.available(partialAlignment.state)
                if (!traceReachedEnd) {
                    val event = events[partialAlignment.tracePosition]
                    for (dec in decisions) {
                        val a = align(partialAlignment, dec, event)
                        if (a != null) {
                            match = true
                            if (bestCost[a.alignmentState] > a.cost)
                                queue.add(a)
                        }
                    }
                }
                if (!match)
                    for (dec in decisions) {
                        val a = align(partialAlignment, dec, null)
                        if (a != null && bestCost[a.alignmentState] > a.cost)
                            queue.add(a)
                    }
            }
            if (!match && !traceReachedEnd) {
                val a = align(partialAlignment, null, events[partialAlignment.tracePosition])
                if (a != null && bestCost[a.alignmentState] > a.cost)
                    queue.add(a)
            }
        }
        throw IllegalStateException("BUG: It was impossible to align the trace with the model.")
    }

    val optimalAlignment: List<Alignment> by lazy {
        val n = if (logger.isDebugEnabled) log.traces.count() else 0
        log.traces.mapIndexed { idx, trace ->
//            logger.debug ( "Optimal alignment progress: ${idx + 1}/$n")
            computeOptimalAlignment(trace).first
        }.toList()
    }

    /**
     * Cost of the shortest valid sequence in the model
     */
    internal val movem: Double by lazy {
        computeOptimalAlignment(Trace(emptySequence())).first.cost
    }

    internal val movel: Double by lazy {
        log.traces.sumByDouble { trace -> trace.events.sumByDouble { distance(null, it) } }
    }

    val fcost: Double by lazy {
        optimalAlignment.sumByDouble { it.cost }
    }

    val fitness: Double by lazy {
        1.0 - fcost / (movel + optimalAlignment.size * movem)
    }

    private val prefix2Step: Map<List<Activity>, List<AlignmentStep>> by lazy {
        val result = HashMap<List<Activity>, ArrayList<AlignmentStep>>()
        for (alignment in optimalAlignment) {
            val prefix = ArrayList<Activity>()
            for (step in alignment.alignment) {
                result.getOrPut(ArrayList(prefix)) { ArrayList() }.add(step)
                if (step.activity != null)
                    prefix.add(step.activity)
            }
        }
        return@lazy result
    }

    private val prefix2Possible: Map<List<Activity>, Set<Activity>> by lazy {
        prefix2Step.keys.associateWith { prefix ->
            allFreePartialAlignments(prefix)
                .flatMapTo(HashSet()) { alignment ->
                    alignment.state.map { it.target }
                }
        }
    }

    private fun pnew(w: Int, n: Int): Double =
        if (n >= w + 2) (w * (w + 1)).toDouble() / (n * (n - 1)).toDouble() else 1.0

    private val generalization: Pair<Double, Double> by lazy {
        val state2Event = Counter<CausalNetState>()
        val state2Activity = HashMap<CausalNetState, HashSet<Activity>>()
        for (alignment in optimalAlignment) {
            val steps = alignment.alignment.toList()
            for ((sidx, step) in alignment.alignment.withIndex()) {
                if (sidx > 0 && step.activity != null && step.event != null) {
                    var lastPossiblyFreeState: CausalNetState? = null
                    for (bidx in sidx - 1 downTo 0) {
                        val before = steps[bidx]
                        val ready = model.available(before.stateBefore).mapToSet { it.activity }
                        if (step.activity !in ready) {
                            lastPossiblyFreeState = before.stateBefore
                            break
                        }
                    }
                    check(lastPossiblyFreeState != null) { "No free state for ${step.activity}. This is outright impossible, there should always be a free state right before start." }
                    state2Event.inc(lastPossiblyFreeState)
                    state2Activity.getOrPut(lastPossiblyFreeState) { HashSet() }.add(step.activity)
                }
            }
        }
        var eventLevel = 0.0
        var stateLevel = 0.0
        var nEvents = 0
        val nStates = state2Activity.keys.size
        for ((s, diff) in state2Activity) {
            val w = diff.size
            val n = state2Event[s]
            val p = pnew(w, n)
            eventLevel += n * p
            stateLevel += p
            nEvents += n
        }
        return@lazy (1.0 - eventLevel / nEvents) to (1.0 - stateLevel / nStates)
    }

    val eventLevelGeneralization: Double
        get() = generalization.first

    val stateLevelGeneralization: Double
        get() = generalization.second

    val precision: Double by lazy {
        var result = 0.0
        val prefix2Next = prefix2Step.mapValues { it.value.mapToSet { it.activity }.filterNotNull() }
//      check(prefix2Possible[emptyList()].isNullOrEmpty())
        var ctr = 0
        for (alignment in optimalAlignment) {
            val prefix = ArrayList<Activity>()
            for (step in alignment.alignment) {
                if (step.activity != null) {
                    val observed = prefix2Next.getValue(prefix)
                    val possible = if (prefix.isNotEmpty()) prefix2Possible.getValue(prefix) else setOf(model.start)
                    logger().trace { "observed=$observed possible=$possible" }
                    check(possible.containsAll(observed)) { "prefix=$prefix observed=$observed possible=$possible" }
                    result += observed.size.toDouble() / possible.size.toDouble()
                    ctr += 1
                    prefix.add(step.activity)
                }
            }
        }
        return@lazy result / ctr
    }

    override fun toString(): String =
        "fitness=$fitness precision=$precision eGeneralization=$eventLevelGeneralization sGeneralization=$stateLevelGeneralization"

}