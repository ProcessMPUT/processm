package processm.enhancement.metadata

import processm.conformance.models.alignments.Alignment
import processm.conformance.models.alignments.CompositeAligner
import processm.core.helpers.map2d.DoublingMap2D
import processm.core.log.*
import processm.core.log.attribute.value
import processm.core.models.causalnet.DecoupledNodeExecution
import processm.core.models.commons.Activity
import processm.core.models.commons.ProcessModel
import processm.core.models.metadata.*
import processm.core.models.metadata.BasicMetadata.BASIC_TIME_STATISTICS
import java.time.Duration


/**
 * Constructs metadata providers for the statistics listed in [BASIC_TIME_STATISTICS] from [Alignment]s.
 * It assumes that the underlying [Event]s have these statistics already computed, and that they are represented
 * as [String]s that can be parsed to [Duration]  by [Duration.parse]
 *
 * The metadata values offered by the providers are of type [DurationDistributionMetadata]
 */
fun Sequence<Alignment>.getTimesMetadataProviders(): List<MetadataProvider> {
    val stats = DoublingMap2D<Activity, URN, ArrayList<Duration>>()
    for (alignment in this) {
        for (step in alignment.steps) {
            val event = step.logMove ?: continue
            val activity = step.modelMove ?: continue
            for (key in BASIC_TIME_STATISTICS) {
                val value = Duration.parse(event.attributes[key.urn]?.value?.toString() ?: continue)
                stats.compute(activity, key) { _, _, old -> old ?: ArrayList() }!!.add(value)
            }
        }
    }
    val result = ArrayList<DefaultMetadataProvider<DurationDistributionMetadata>>()
    for (key in BASIC_TIME_STATISTICS) {
        val column = stats.getColumn(key)
        val provider = DefaultMetadataProvider<DurationDistributionMetadata>(key)
        val secondary = HashMap<Activity, ArrayList<Duration>>()
        for ((activity, values) in column.entries) {
            provider.put(activity, DurationDistributionMetadata(values))
            if (activity is DecoupledNodeExecution)
                secondary.computeIfAbsent(activity.activity) { ArrayList() }.addAll(values)
        }
        for ((activity, values) in secondary)
            provider.put(activity, DurationDistributionMetadata(values))
        if (provider.isNotEmpty())
            result.add(provider)
    }
    return result
}

/**
 * Given a [stream] with [Event.lifecycleTransition] correctly filled, it uses [InferTimes] to compute the statistics
 * given by [BASIC_TIME_STATISTICS], then computes alignments between the [model] and the [stream] using [CompositeAligner],
 * aggregating [Event]s with [AggregateConceptInstanceToSingleEvent].
 * Finally, the computed metadata for [Activity]ies are added to the handler.
 */
fun MutableMetadataHandler.extendWithTimesMetadataFromStream(model: ProcessModel, stream: XESInputStream) {
    val aligner = CompositeAligner(model)
    sequence {
        val buffer = ArrayList<Event>()
        suspend fun SequenceScope<Alignment>.flush() {
            if (buffer.isNotEmpty()) {
                yield(aligner.align(processm.core.log.hierarchical.Trace(buffer.asSequence())))
                buffer.clear()
            }
        }
        for (component in AggregateConceptInstanceToSingleEvent(InferTimes(stream))) {
            when (component) {
                is Log -> continue
                is Trace -> flush()
                is Event -> buffer.add(component)
            }
        }
        flush()
    }.getTimesMetadataProviders().forEach(this::addMetadataProvider)
}

fun <T> T.extendWithTimesMetadataFromStream(stream: XESInputStream) where T : MutableMetadataHandler, T : ProcessModel =
    this.extendWithTimesMetadataFromStream(this, stream)