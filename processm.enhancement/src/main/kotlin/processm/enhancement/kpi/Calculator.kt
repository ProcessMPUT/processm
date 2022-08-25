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
import processm.core.helpers.map2d.DoublingMap2D
import processm.core.helpers.stats.Distribution
import processm.core.log.attribute.Attribute
import processm.core.log.attribute.IntAttr
import processm.core.log.attribute.RealAttr
import processm.core.log.attribute.value
import processm.core.log.hierarchical.Log
import processm.core.models.causalnet.DecoupledNodeExecution
import processm.core.models.commons.Activity
import processm.core.models.commons.Arc
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

    private data class NumericAttribute(val key: String, val attributeValue: Double)
    private data class TokenWithPayload(val source: Transition, val attributes: List<NumericAttribute>)
    private class FIFOTokenPool {
        private val data = ArrayDeque<TokenWithPayload>()
        fun put(token: TokenWithPayload) = data.addLast(token)
        fun get(): TokenWithPayload = data.removeFirst()
    }

    private class RawArcKPI(
        val inbound: ArrayList<Double> = ArrayList(),
        val outbound: ArrayList<Double> = ArrayList()
    )

    /**
     * Calculates KPI report from all numeric attributes spotted in the [logs].
     */
    fun calculate(logs: Sequence<Log>): Report {


        val logKPIraw = HashMap<String, ArrayList<Double>>()
        val traceKPIraw = HashMap<String, ArrayList<Double>>()
        val eventKPIraw = DoublingMap2D<String, Activity?, ArrayList<Double>>()
        val arcKPIraw = DoublingMap2D<String, Arc, RawArcKPI>()

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

            val tokens = HashMap<Place, FIFOTokenPool>()
            val ptExecutionHistory = ArrayList<Pair<ProcessTreeActivity, List<NumericAttribute>>>()
            val ptArcs = DoublingMap2D<ProcessTreeActivity, ProcessTreeActivity, VirtualProcessTreeArc>()
            (model as? ProcessTree)?.generateArcs(includeSilent = true)?.forEach { ptArcs[it.source, it.target] = it }

            for (alignment in alignments) {
                tokens.clear()
                ptExecutionHistory.clear()
                for (step in alignment.steps) {
                    val activity = when (step.type) {
                        DeviationType.None -> (step.modelMove as? DecoupledNodeExecution)?.activity ?: step.modelMove!!
                        DeviationType.LogDeviation -> null
                        DeviationType.ModelDeviation -> continue // no-way to get values for the model-only moves
                    }

                    val decoupledNodeExecution = step.modelMove as? DecoupledNodeExecution
                    val transition = activity as? Transition
                    val ptActivity = activity as? ProcessTreeActivity

                    val inArcs = transition?.inPlaces?.mapNotNull { place ->
                        val (source, attributes) = tokens[place]?.get() ?: return@mapNotNull null
                        val arc = VirtualPetriNetArc(source, transition, place)
                        attributes.forEach { (key, attributeValue) ->
                            arcKPIraw.compute(key, arc) { _, _, old ->
                                (old ?: RawArcKPI()).apply { outbound.add(attributeValue) }
                            }
                        }
                        return@mapNotNull arc
                    }

                    val rawValues =
                        step.logMove!!.attributes.mapNotNull { (key, attribute) ->
                            if (attribute.isNumeric()) NumericAttribute(key, attribute.toDouble()) else null
                        }

                    if (ptActivity !== null)
                        for ((previous, previousAttributes) in ptExecutionHistory.reversed()) {
                            val arc = ptArcs[previous, ptActivity]
                            if (arc !== null) {
                                for ((key, attributeValue) in rawValues) {
                                    arcKPIraw.compute(key, arc) { _, _, old ->
                                        (old ?: RawArcKPI()).apply { inbound.add(attributeValue) }
                                    }
                                }
                                for ((key, attributeValue) in previousAttributes) {
                                    arcKPIraw.compute(key, arc) { _, _, old ->
                                        (old ?: RawArcKPI()).apply { outbound.add(attributeValue) }
                                    }
                                }
                                break
                            }
                        }

                    transition?.outPlaces?.forEach { place ->
                        tokens.computeIfAbsent(place) { FIFOTokenPool() }.put(TokenWithPayload(transition, rawValues))
                    }

                    for ((key, attributeValue) in rawValues) {

                        eventKPIraw.compute(key, activity) { _, _, old ->
                            (old ?: ArrayList()).apply { add(attributeValue) }
                        }

                        (decoupledNodeExecution?.join?.dependencies ?: inArcs)?.forEach { d ->
                            arcKPIraw.compute(key, d) { _, _, old ->
                                (old ?: RawArcKPI()).apply { inbound.add(attributeValue) }
                            }
                        }
                        decoupledNodeExecution?.split?.dependencies?.forEach { d ->
                            arcKPIraw.compute(key, d) { _, _, old ->
                                (old ?: RawArcKPI()).apply { outbound.add(attributeValue) }
                            }
                        }
                    }

                    ptActivity?.let { ptExecutionHistory.add(it to rawValues) }
                }
            }

        }

        val logKPI = logKPIraw.mapValues { (_, v) -> Distribution(v) }
        val traceKPI = traceKPIraw.mapValues { (_, v) -> Distribution(v) }
        val eventKPI = eventKPIraw.mapValues { _, _, v -> Distribution(v) }
        val arcKPI = arcKPIraw.mapValues { _, _, v ->
            ArcKPI(
                if (v.inbound.isNotEmpty()) Distribution(v.inbound) else null,
                if (v.outbound.isNotEmpty()) Distribution(v.outbound) else null
            )
        }
        return Report(logKPI, traceKPI, eventKPI, arcKPI)
    }

    /**
     * Calculates KPI report from all numeric attributes spotted in the [log].
     */
    fun calculate(log: Log): Report = calculate(sequenceOf(log))

    private fun Attribute<*>.isNumeric(): Boolean = this is IntAttr || this is RealAttr
    private fun Attribute<*>.toDouble(): Double = (this.value as Number).toDouble()
}

