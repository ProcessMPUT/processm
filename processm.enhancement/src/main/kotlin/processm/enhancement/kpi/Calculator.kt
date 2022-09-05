package processm.enhancement.kpi

import processm.conformance.models.DeviationType
import processm.conformance.models.alignments.AStar
import processm.conformance.models.alignments.Aligner
import processm.conformance.models.alignments.AlignerFactory
import processm.conformance.models.alignments.CompositeAligner
import processm.conformance.models.alignments.cache.CachingAlignerFactory
import processm.conformance.models.alignments.cache.DefaultAlignmentCache
import processm.conformance.models.alignments.events.DefaultEventsSummarizer
import processm.conformance.models.alignments.events.EventsSummarizer
import processm.conformance.models.alignments.petrinet.DecompositionAligner
import processm.core.helpers.MRUMap
import processm.core.helpers.map2d.DoublingMap2D
import processm.core.helpers.stats.Distribution
import processm.core.log.attribute.Attribute
import processm.core.log.attribute.IntAttr
import processm.core.log.attribute.RealAttr
import processm.core.log.attribute.value
import processm.core.log.hierarchical.Log
import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.DecoupledNodeExecution
import processm.core.models.commons.Activity
import processm.core.models.commons.CausalArc
import processm.core.models.commons.ProcessModel
import processm.core.models.petrinet.PetriNet
import processm.core.models.petrinet.Place
import processm.core.models.petrinet.Transition
import processm.core.models.processtree.*
import kotlin.sequences.Sequence

/**
 * A KPI calculator.
 * @property model The process model.
 * @property aligner The aligner for aligning log and the [model].
 * @property eventsSummarizer The event summarizer that assigns grouping identity to process traces for reuse of
 * alignments of different traces of the same process variant.
 */
class Calculator(
    val model: ProcessModel,
    val aligner: Aligner = getDefaultAligner(model),
    val eventsSummarizer: EventsSummarizer<*> = DefaultEventsSummarizer
) {
    companion object {
        private val ProcessTreeDecompositionAlignerFactory = AlignerFactory { model, penalty, _ ->
            processm.conformance.models.alignments.processtree.DecompositionAligner(model as ProcessTree, penalty)
        }

        private fun getDefaultAligner(model: ProcessModel): Aligner {
            val cache = DefaultAlignmentCache()
            val astarFactory = CachingAlignerFactory(cache) { model, penalty, _ -> AStar(model, penalty) }
            val factories = listOfNotNull(
                astarFactory,
                if (model is PetriNet)
                    CachingAlignerFactory(cache) { model, penalty, pool ->
                        DecompositionAligner(
                            model as PetriNet,
                            penalty,
                            pool = pool,
                            alignerFactory = astarFactory
                        )
                    }
                else null,
                if (model is ProcessTree) ProcessTreeDecompositionAlignerFactory
                else null
            ).toTypedArray()
            return CompositeAligner(model, cache = null, alignerFactories = factories)
        }
    }

    /**
     * An auxiliary class to store the value of the attribute [key] as a [Double], to be eventually added to a [Distribution]
     */
    private data class NumericAttribute(val key: String, val attributeValue: Double)

    /**
     * An auxiliary class mimicking [ArcKPI], but using [ArrayList] instead of [Distribution]
     */
    private class RawArcKPI(val inbound: ArrayList<Double> = ArrayList(), val outbound: ArrayList<Double> = ArrayList())

    /**
     * A base class for per-model type handler of arc KPIs
     *
     * @param arcKPIraw The target for the KPIs found by the handler
     */
    private abstract class ArcKPIHandler(val arcKPIraw: DoublingMap2D<String, CausalArc, RawArcKPI>) {

        /**
         * Called before processing each trace
         */
        open fun reset() {}

        /**
         * Called for each alignment step with a non-null model move
         *
         * @param activity Contains the value of [processm.conformance.models.alignments.Step.modelMove]
         * @param rawValues Numeric attributes retrieved for the [activity]
         */
        abstract fun step(activity: Activity, rawValues: List<NumericAttribute>)
    }

    /**
     * [ArcKPIHandler] for [ProcessTree]s, operating on [VirtualProcessTreeCausalArc]s, i.e., arcs connecting activities,
     * instead of arcs connecting activities with internal nodes. Similarily for [PetriNetArcKPIHandler], this enables
     * computing more interesting KPIs (otherwise they would be no different than those already computed for activities),
     * at the cost of using a heuristics. More details are given in the description of [step].
     */
    private class ProcessTreeArcKPIHandler(model: ProcessTree, arcKPIraw: DoublingMap2D<String, CausalArc, RawArcKPI>) :
        ArcKPIHandler(arcKPIraw) {

        /**
         * For each activity the most recent value of its numeric attributes. It maintains order, so the most recently executed activity is last when iterating over it.
         */
        private val history = MRUMap<ProcessTreeActivity, List<NumericAttribute>>()

        /**
         * For each activity, a list of [VirtualProcessTreeMultiArc]s pointing to it.
         */
        private val arcs = HashMap<ProcessTreeActivity, ArrayList<VirtualProcessTreeMultiArc>>().apply {
            model.generateArcs().forEach { computeIfAbsent(it.target) { ArrayList() }.add(it) }
        }

        override fun reset() = history.clear()

        /**
         * Finds a multi-arc from [arcs] such that its target is [activity] and its sources were the most recently executed.
         * It prefers the most extensive hypothesis, e.g., for the process tree `⟲(a, ∧(b,c), b)` and the trace `a c b a`
         * it will assume that both `c` and `b` are causes for `a`. This, unfortunately, may be an overly greedy strategy.
         * Consider: `⟲(a, →(b, ×(c, ∧(b,c))))` and the trace `a b c a`. The activity `b` is executed as the first element
         * of the sequence, not as a parallel task in `∧(b,c)`, yet [step] will claim that both `b` and `c` are causes for `a`,
         * yet such a phenomenon would not occur should we replace the second `b` with another activity, e.g., `⟲(a, →(b, ×(c, ∧(d,c))))`
         * This possibly could be solved should there be [InternalNode]s in alignments, as one could then follow a path of
         * internal nodes and ensure that one activity is a direct cause of another. Currently, it is not obvious that
         * it is worth the effort.
         */
        override fun step(activity: Activity, rawValues: List<NumericAttribute>) {
            require(activity is ProcessTreeActivity)
            //There cannot be repetitions in history, because it is a map. It is thus sufficient to decrease a counter
            //each time we visit a candidate, as we cannot decrease the counter more than once for the same activity.
            //A similar technique is used in "Artificial Intelligence: A Modern Approach" in the forward-chaining algorithm
            //for reasoning in the propositional logic.
            class Candidate(var ctr: Int, val arc: VirtualProcessTreeMultiArc)

            val candidates = HashMap<ProcessTreeActivity, ArrayList<Candidate>>()
            arcs[activity]?.forEach { arc ->
                assert(arc.sources.isNotEmpty())
                val candidate = Candidate(arc.sources.size, arc)
                // The same candidate is shared between multiple entries in candidates to access the same, shared counter
                arc.sources.forEach { src -> candidates.computeIfAbsent(src) { ArrayList() }.add(candidate) }
            }
            if (candidates.isNotEmpty()) {
                val acceptedCandidates = ArrayList<VirtualProcessTreeMultiArc>()
                historyLoop@ for (previous in history.keys.reversed()) {
                    for (candidate in candidates[previous].orEmpty()) {
                        if (candidate.ctr > 0) {
                            candidate.ctr--
                            if (candidate.ctr == 0)
                                acceptedCandidates.add(candidate.arc)
                        }
                    }
                    if (previous == activity)
                        break@historyLoop   // if we arrived at the previous execution of the current activity, we cannot search any further
                }
                if (acceptedCandidates.isNotEmpty()) {
                    val multiArc = acceptedCandidates.maxBy { it.sources.size }
                    for (arc in multiArc.toArcs()) {
                        for ((key, attributeValue) in rawValues) {
                            arcKPIraw.compute(key, arc) { _, _, old ->
                                (old ?: RawArcKPI()).apply { inbound.add(attributeValue) }
                            }
                        }
                        for ((key, attributeValue) in history[arc.source]!!) {
                            arcKPIraw.compute(key, arc) { _, _, old ->
                                (old ?: RawArcKPI()).apply { outbound.add(attributeValue) }
                            }
                        }
                    }
                }
            }
            history[activity] = rawValues
        }
    }

    /**
     * A straightforward [ArcKPIHandler] for [CausalNet]s, leveraging the fact that [DecoupledNodeExecution] used by [Aligner]
     * reports both bindings.
     */
    private class CNetArcKPIHandler(arcKPIraw: DoublingMap2D<String, CausalArc, RawArcKPI>) : ArcKPIHandler(arcKPIraw) {

        override fun step(activity: Activity, rawValues: List<NumericAttribute>) {
            check(activity is DecoupledNodeExecution)
            for ((key, attributeValue) in rawValues) {
                activity.join?.dependencies?.forEach { d ->
                    arcKPIraw.compute(key, d) { _, _, old ->
                        (old ?: RawArcKPI()).apply { inbound.add(attributeValue) }
                    }
                }
                activity.split?.dependencies?.forEach { d ->
                    arcKPIraw.compute(key, d) { _, _, old ->
                        (old ?: RawArcKPI()).apply { outbound.add(attributeValue) }
                    }
                }
            }
        }
    }

    /**
     * A [ArcKPIHandler] for [PetriNet]s operating on [VirtualPetriNetCausalArc]s, i.e., arcs consisting of two real arcs:
     * one from a transition to a place, and the other from the place to another transition. As places are, necessarily,
     * without any KPIs, this approach allows for more interesting KPIs leveraging knowledge from two transitions at the
     * same time. The drawback is that must assume some order of consuming tokens: currently, it is FIFO.
     */
    private class PetriNetArcKPIHandler(arcKPIraw: DoublingMap2D<String, CausalArc, RawArcKPI>) :
        ArcKPIHandler(arcKPIraw) {

        private data class TokenWithPayload(val source: Transition, val rawValues: List<NumericAttribute>)

        private val tokens = HashMap<Place, ArrayDeque<TokenWithPayload>>()
        override fun reset() = tokens.clear()

        override fun step(activity: Activity, rawValues: List<NumericAttribute>) {
            check(activity is Transition)
            activity.inPlaces.forEach { place ->
                val (source, oldRawValues) = tokens[place]?.removeFirst() ?: return@forEach
                val arc = VirtualPetriNetCausalArc(source, activity, place)
                oldRawValues.forEach { (key, attributeValue) ->
                    arcKPIraw.compute(key, arc) { _, _, old ->
                        (old ?: RawArcKPI()).apply { outbound.add(attributeValue) }
                    }
                }
                for ((key, attributeValue) in rawValues) {
                    arcKPIraw.compute(key, arc) { _, _, old ->
                        (old ?: RawArcKPI()).apply { inbound.add(attributeValue) }
                    }
                }
            }

            activity.outPlaces.forEach { place ->
                tokens.computeIfAbsent(place) { ArrayDeque() }.addLast(TokenWithPayload(activity, rawValues))
            }
        }

    }

    /**
     * Calculates KPI report from all numeric attributes spotted in the [logs].
     */
    fun calculate(logs: Sequence<Log>): Report {


        val logKPIraw = HashMap<String, ArrayList<Double>>()
        val traceKPIraw = HashMap<String, ArrayList<Double>>()
        val eventKPIraw = DoublingMap2D<String, Activity?, ArrayList<Double>>()
        val arcKPIraw = DoublingMap2D<String, CausalArc, RawArcKPI>()

        val arcKPIHandler: ArcKPIHandler = when (model) {
            is ProcessTree -> ProcessTreeArcKPIHandler(model, arcKPIraw)
            is CausalNet -> CNetArcKPIHandler(arcKPIraw)
            is PetriNet -> PetriNetArcKPIHandler(arcKPIraw)
            else -> throw UnsupportedOperationException("Process models of type ${model::class} are not supported")
        }

        for (log in logs) {
            for (attribute in log.attributes.values) {
                if (!attribute.isNumeric())
                    continue

                logKPIraw.compute(attribute.key) { _, old ->
                    (old ?: ArrayList()).apply { add(attribute.toDouble()) }
                }
            }

            for (trace in log.traces) {
                for ((key, attribute) in trace.attributes) {
                    if (!attribute.isNumeric())
                        continue

                    traceKPIraw.compute(key) { _, old ->
                        (old ?: ArrayList()).apply { add(attribute.toDouble()) }
                    }
                }
            }

            val alignments = aligner.align(log, eventsSummarizer)

            for (alignment in alignments) {
                arcKPIHandler.reset()
                for (step in alignment.steps) {
                    val activity = when (step.type) {
                        DeviationType.None -> (step.modelMove as? DecoupledNodeExecution)?.activity ?: step.modelMove!!
                        DeviationType.LogDeviation -> null
                        DeviationType.ModelDeviation -> continue // no-way to get values for the model-only moves
                    }

                    val rawValues = step.logMove!!.attributes.mapNotNull { (key, attribute) ->
                        if (attribute.isNumeric()) NumericAttribute(key, attribute.toDouble()) else null
                    }

                    for ((key, attributeValue) in rawValues) {
                        eventKPIraw.compute(key, activity) { _, _, old ->
                            (old ?: ArrayList()).apply { add(attributeValue) }
                        }
                    }

                    step.modelMove?.let { arcKPIHandler.step(it, rawValues) }
                }
            }

        }

        val logKPI = logKPIraw.mapValues { (_, v) -> Distribution(v) }
        val traceKPI = traceKPIraw.mapValues { (_, v) -> Distribution(v) }
        val eventKPI = eventKPIraw.mapValues { _, _, v -> Distribution(v) }
        val inboundArcKPI = arcKPIraw.mapValuesNotNull { _, _, v ->
            if (v.inbound.isNotEmpty()) Distribution(v.inbound) else null
        }
        val outboundArcKPI = arcKPIraw.mapValuesNotNull { _, _, v ->
            if (v.outbound.isNotEmpty()) Distribution(v.outbound) else null
        }
        return Report(logKPI, traceKPI, eventKPI, outboundArcKPI, inboundArcKPI)
    }

    /**
     * Calculates KPI report from all numeric attributes spotted in the [log].
     */
    fun calculate(log: Log): Report = calculate(sequenceOf(log))

    private fun Attribute<*>.isNumeric(): Boolean = this is IntAttr || this is RealAttr
    private fun Attribute<*>.toDouble(): Double = (this.value as Number).toDouble()
}

