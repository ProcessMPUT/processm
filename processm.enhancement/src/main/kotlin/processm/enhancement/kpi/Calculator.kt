package processm.enhancement.kpi

import processm.conformance.measures.Fitness
import processm.conformance.measures.complexity.CFC
import processm.conformance.measures.complexity.Halstead
import processm.conformance.measures.complexity.NOAC
import processm.conformance.measures.precision.ETCPrecision
import processm.conformance.models.DeviationType
import processm.conformance.models.alignments.*
import processm.conformance.models.alignments.cache.CachingAlignerFactory
import processm.conformance.models.alignments.cache.DefaultAlignmentCache
import processm.conformance.models.alignments.events.DefaultEventsSummarizer
import processm.conformance.models.alignments.events.EventsSummarizer
import processm.conformance.models.alignments.petrinet.DecompositionAligner
import processm.core.log.AggregateConceptInstanceToSingleEvent
import processm.core.log.InferConceptInstanceFromStandardLifecycle
import processm.core.log.InferTimes
import processm.core.log.hierarchical.HoneyBadgerHierarchicalXESInputStream
import processm.core.log.hierarchical.InMemoryXESProcessing
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.toFlatSequence
import processm.core.models.causalnet.DecoupledNodeExecution
import processm.core.models.commons.Activity
import processm.core.models.commons.CausalArc
import processm.core.models.commons.ProcessModel
import processm.core.models.metadata.BasicMetadata.BASIC_TIME_STATISTICS
import processm.core.models.metadata.BasicMetadata.COUNT
import processm.core.models.metadata.BasicMetadata.WAITING_TIME
import processm.core.models.metadata.URN
import processm.core.models.petrinet.PetriNet
import processm.core.models.processtree.ProcessTree
import processm.helpers.firstOrNull
import processm.helpers.indexOfLast
import processm.helpers.lastOrNull
import processm.helpers.map2d.DoublingMap2D
import processm.helpers.stats.Distribution
import processm.helpers.time.meanOf
import processm.helpers.totalDays
import java.time.Duration
import java.time.Instant

/**
 * A KPI calculator.
 * @property aligner The aligner for aligning log and the [model].
 * @property eventsSummarizer The event summarizer that assigns grouping identity to process traces for reuse of
 * alignments of different traces of the same process variant.
 */
class Calculator(
    val aligner: Aligner,
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

    private val fitness = Fitness(aligner)
    private val precision = ETCPrecision(aligner.model)

    /**
     * Creates new instance of the KPI calculator.
     * @param model The process model.
     * @param eventsSummarizer The event summarizer that assigns grouping identity to process traces for reuse of
     * alignments of different traces of the same process variant.
     */
    constructor(
        model: ProcessModel,
        eventsSummarizer: EventsSummarizer<*> = DefaultEventsSummarizer
    ) : this(getDefaultAligner(model), eventsSummarizer)

    /**
     * An auxiliary class to store the value of the attribute [key] as a [Double], to be eventually added to a [Distribution]
     */
    private data class NumericAttribute(val key: String, val attributeValue: Double)

    /**
     * Calculates KPI report from all numeric attributes spotted in the [logs].
     */
    @OptIn(InMemoryXESProcessing::class)
    fun calculate(logs: Sequence<Log>): Report {
        val logKPIraw = HashMap<String, ArrayList<Double>>()
        val traceKPIraw = HashMap<String, ArrayList<Double>>()
        val eventKPIraw = DoublingMap2D<String, Activity?, ArrayList<Double>>()
        val arcKPIraw = DoublingMap2D<String, CausalArc, ArrayList<Double>>()
        val alignmentList = ArrayList<Alignment>()
        var start = 0

        for (_log in logs) {
            var baseXESStream = _log.toFlatSequence()
            if (_log.lifecycleModel === null || _log.lifecycleModel.equals("standard", true))
                baseXESStream = InferConceptInstanceFromStandardLifecycle(baseXESStream)
            baseXESStream = AggregateConceptInstanceToSingleEvent(InferTimes(baseXESStream))
            // FIXME: rewrite to not materialize the entire stream in the memory
            val log = HoneyBadgerHierarchicalXESInputStream(baseXESStream).first()

            for (entry in log.attributes.entries) {
                val (key, _) = entry
                val value = entry.toDouble() ?: continue

                logKPIraw.compute(key) { _, old ->
                    (old ?: ArrayList()).apply { add(value) }
                }
            }
            logKPIraw.compute(COUNT.urn) { _, old ->
                (old ?: ArrayList()).apply { if (isEmpty()) add(1.0) else set(0, get(0) + 1.0) }
            }

            for (trace in log.traces) {
                for (entry in trace.attributes) {
                    val (key, _) = entry
                    val value = entry.toDouble() ?: continue

                    traceKPIraw.compute(key) { _, old ->
                        (old ?: ArrayList()).apply { add(value) }
                    }
                }

                traceKPIraw.compute(COUNT.urn) { _, old ->
                    (old ?: ArrayList()).apply { if (isEmpty()) add(1.0) else set(0, get(0) + 1.0) }
                }
            }

            val alignments = aligner.align(log, eventsSummarizer)
            start = alignmentList.size

            for (alignment in alignments) {
                for ((index, step) in alignment.steps.withIndex()) {
                    val activity = when (step.type) {
                        DeviationType.None -> (step.modelMove as? DecoupledNodeExecution)?.activity ?: step.modelMove!!
                        DeviationType.LogDeviation -> null
                        DeviationType.ModelDeviation -> continue // no-way to get values for the model-only moves
                    }

                    val rawValues = step.logMove!!.attributes.mapNotNull { entry ->
                        val (key, _) = entry
                        val value = entry.toDouble()
                        value?.let { NumericAttribute(key, it) }
                    }

                    for ((key, attributeValue) in rawValues) {
                        eventKPIraw.compute(key, activity) { _, _, old ->
                            (old ?: ArrayList()).apply { add(attributeValue) }
                        }
                    }
                    eventKPIraw.compute(COUNT.urn, activity) { _, _, old ->
                        (old ?: ArrayList()).apply { if (isEmpty()) add(1.0) else set(0, get(0) + 1.0) }
                    }

                    if (activity !== null) {
                        val steps = alignment.steps
                        val currentTimestamp = alignment.estimateTimestamp(index)
                        for (cause in step.modelCause) {
                            val causingStepIndex = steps.indexOfLast(index) { it.modelMove eq cause }
                            assert(causingStepIndex in steps.indices) { "causingStepIndex: $causingStepIndex" }
                            val arc = Arc(cause, step.modelMove!!)

                            if (currentTimestamp !== null) {
                                val causeTimestamp = alignment.estimateTimestamp(causingStepIndex)
                                if (causeTimestamp !== null) {
                                    val waitingTime = Duration.between(causeTimestamp, currentTimestamp)
                                    arcKPIraw.compute(WAITING_TIME.urn, arc) { _, _, old ->
                                        (old ?: ArrayList()).apply { add(waitingTime.totalDays) }
                                    }
                                }
                            }

                            arcKPIraw.compute(COUNT.urn, arc) { _, _, old ->
                                (old ?: ArrayList()).apply { if (isEmpty()) add(1.0) else set(0, get(0) + 1.0) }
                            }
                        }
                    }
                }

                alignmentList.add(alignment)
                logKPIraw.compute(Fitness.URN.urn) { _, old ->
                    (old ?: ArrayList()).apply {
                        add(fitness(log, alignmentList.subList(start, alignmentList.size)))
                    }
                }
                logKPIraw.compute(ETCPrecision.URN.urn) { _, old ->
                    (old ?: ArrayList()).apply {
                        add(precision(log))
                    }
                }
            }

        }

        val logKPI = logKPIraw.mapValues { (_, v) -> Distribution(v) }
        val traceKPI = traceKPIraw.mapValues { (_, v) -> Distribution(v) }
        val eventKPI = eventKPIraw.mapValues { _, _, v -> Distribution(v) }
        val arcKPI = arcKPIraw.mapValuesNotNull { _, _, v -> if (v.isNotEmpty()) Distribution(v) else null }
        val halsteadComplexityMetric = Halstead(aligner.model)
        val noac = NOAC(aligner.model)
        val cfc = CFC(aligner.model)
        return Report(logKPI, traceKPI, eventKPI, arcKPI, alignmentList, halsteadComplexityMetric, noac, cfc)
    }

    /**
     * Calculates KPI report from all numeric attributes spotted in the [log].
     */
    fun calculate(log: Log): Report = calculate(sequenceOf(log))

    /**
     * @return null if the value cannot be converted to Double
     */
    private fun Map.Entry<String, Any?>.toDouble(): Double? {
        val v = value // avoid successive calls to value and redundant casts below
        return when (v) {
            is Double -> v
            is Number -> v.toDouble()
            is Instant -> v.toEpochMilli().toDouble()
            else ->
                if (URN.tryParse(key) in BASIC_TIME_STATISTICS)
                    Duration.parse(v as CharSequence).totalDays
                else null
        }
    }

    private fun Alignment.estimateTimestamp(index: Int): Instant? =
        steps[index].logMove?.timeTimestamp ?: let {
            val prev = steps.lastOrNull(index) { it.logMove?.timeTimestamp !== null }?.logMove?.timeTimestamp
            val next = steps.firstOrNull(index) { it.logMove?.timeTimestamp !== null }?.logMove?.timeTimestamp
            return@let when {
                prev !== null && next !== null -> meanOf(prev, next)
                prev !== null -> prev
                next !== null -> next
                else -> null
            }
        }

    private infix fun Activity?.eq(other: Activity?): Boolean =
        this?.name == other?.name && this?.isSilent == other?.isSilent
}

