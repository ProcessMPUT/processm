package processm.performance.kpi

import processm.conformance.models.DeviationType
import processm.conformance.models.alignments.Aligner
import processm.conformance.models.alignments.CompositeAligner
import processm.conformance.models.alignments.events.DefaultEventsSummarizer
import processm.conformance.models.alignments.events.EventsSummarizer
import processm.core.helpers.map2d.DoublingMap2D
import processm.core.log.attribute.Attribute
import processm.core.log.attribute.IntAttr
import processm.core.log.attribute.RealAttr
import processm.core.log.attribute.value
import processm.core.log.hierarchical.Log
import processm.core.models.commons.Activity
import processm.core.models.commons.ProcessModel

/**
 * A KPI calculator.
 * @property model The process model.
 * @property aligner The aligner for aligning log and the [model].
 * @property eventsSummarizer The event summarizer that assigns grouping identity to process traces for reuse of
 * alignments of different traces of the same process variant.
 */
class Calculator(
    val model: ProcessModel,
    val aligner: Aligner = CompositeAligner(model),
    val eventsSummarizer: EventsSummarizer<*> = DefaultEventsSummarizer()
) {
    /**
     * Calculates KPI report from all numeric attributes spotted in the log.
     */
    fun calculate(log: Log): Report {
        val logKPI = log.attributes
            .filterValues { it.isNumeric() }
            .mapValues { (_, attribute) -> attribute.toDouble() }

        val traceKPIraw = HashMap<String, ArrayList<Double>>()
        for (trace in log.traces) {
            for ((key, attribute) in trace.attributes) {
                if (!attribute.isNumeric())
                    continue

                traceKPIraw.compute(key) { _, old ->
                    (old ?: ArrayList()).apply { add(attribute.toDouble()) }
                }
            }
        }
        val traceKPI = traceKPIraw.mapValues { (_, v) -> Distribution(v) }

        val eventKPIraw = DoublingMap2D<String, Activity?, ArrayList<Double>>()
        val alignments = aligner.align(log, eventsSummarizer)
        for (alignment in alignments) {
            for (step in alignment.steps) {
                val activity = when (step.type) {
                    DeviationType.None -> step.modelMove!!
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
        val eventKPI = eventKPIraw.mapValues { _, _, v -> Distribution(v) }

        return Report(log, model, logKPI, traceKPI, eventKPI)
    }

    private fun Attribute<*>.isNumeric(): Boolean = this is IntAttr || this is RealAttr
    private fun Attribute<*>.toDouble(): Double = (this.value as Number).toDouble()
}
