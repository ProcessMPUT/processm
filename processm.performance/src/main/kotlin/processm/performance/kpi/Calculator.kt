package processm.performance.kpi

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
import processm.core.log.attribute.Attribute
import processm.core.log.attribute.IntAttr
import processm.core.log.attribute.RealAttr
import processm.core.log.attribute.value
import processm.core.log.hierarchical.Log
import processm.core.models.causalnet.DecoupledNodeExecution
import processm.core.models.commons.Activity
import processm.core.models.commons.ProcessModel
import processm.core.models.petrinet.PetriNet
import processm.core.models.processtree.ProcessTree

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
    val eventsSummarizer: EventsSummarizer<*> = DefaultEventsSummarizer()
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
     * Calculates KPI report from all numeric attributes spotted in the [logs].
     */
    fun calculate(logs: Sequence<Log>): Report {
        val logKPIraw = HashMap<String, ArrayList<Double>>()
        val traceKPIraw = HashMap<String, ArrayList<Double>>()
        val eventKPIraw = DoublingMap2D<String, Activity?, ArrayList<Double>>()

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
                for (step in alignment.steps) {
                    val activity = when (step.type) {
                        DeviationType.None -> (step.modelMove as? DecoupledNodeExecution)?.activity ?: step.modelMove!!
                        DeviationType.LogDeviation -> null
                        DeviationType.ModelDeviation -> continue // no-way to get values for the model-only moves
                    }

                    for ((key, attribute) in step.logMove!!.attributes) {
                        if (!attribute.isNumeric())
                            continue

                        eventKPIraw.compute(key, activity) { _, _, old ->
                            (old ?: ArrayList()).apply { add(attribute.toDouble()) }
                        }
                    }
                }
            }

        }

        val logKPI = logKPIraw.mapValues { (_, v) -> Distribution(v) }
        val traceKPI = traceKPIraw.mapValues { (_, v) -> Distribution(v) }
        val eventKPI = eventKPIraw.mapValues { _, _, v -> Distribution(v) }
        return Report(logKPI, traceKPI, eventKPI)
    }

    /**
     * Calculates KPI report from all numeric attributes spotted in the [log].
     */
    fun calculate(log: Log): Report = calculate(sequenceOf(log))

    private fun Attribute<*>.isNumeric(): Boolean = this is IntAttr || this is RealAttr
    private fun Attribute<*>.toDouble(): Double = (this.value as Number).toDouble()
}
