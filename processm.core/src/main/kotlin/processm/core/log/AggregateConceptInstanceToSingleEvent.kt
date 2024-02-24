package processm.core.log

import processm.helpers.map2d.DoublingMap2D

/**
 * A transforming stream, aggregating all events in [base] sharing the same [Event.conceptName] and [Event.conceptInstance] within a single trace to a single event.
 * [Trace]s and [Log]s remain unchanged.
 * The details of aggregation are implemented by [aggregator] (see below); the default value is suitable for use with [InferTimes].
 *
 * @param aggregator Receives a list of pairs, each pair consists of an [Event] and its 0-indexed position in the trace.
 * All events in the list share the same values of [Event.conceptName] and [Event.conceptInstance].
 * It should return either a pair with the same semantics or `null`, the latter indicating that this pair
 * ([Event.conceptName], [Event.conceptInstance]) should be ignored.
 * The position in the trace (i.e., the first component of the returned pair) can be modified, although the values
 * returned by different calls to [aggregator] in the context of a single trace should yield a total order on the
 * returned pairs.
 */
class AggregateConceptInstanceToSingleEvent(
    val base: XESInputStream,
    val aggregator: (List<Pair<Int, Event>>) -> Pair<Int, Event>? = List<Pair<Int, Event>>::last
) : XESInputStream {
    override fun iterator(): Iterator<XESComponent> = iterator {
        // Key: concept:name, concept:instance; Value: events sharing the key
        val buffer = DoublingMap2D<String?, String?, ArrayList<Pair<Int, Event>>>()
        var ctr = 0
        suspend fun SequenceScope<XESComponent>.flushBuffer() {
            val tmp = buffer.values.mapNotNullTo(ArrayList(), aggregator)
            tmp.sortBy { it.first }
            tmp.forEach { yield(it.second) }
            ctr = 0
            buffer.clear()
        }
        for (component in base) {
            when (component) {
                is Event -> {
                    buffer
                        .compute(component.conceptName, component.conceptInstance) { _, _, old -> old ?: ArrayList() }!!
                        .add(ctr to component)
                    ctr++
                }
                is Trace, is Log -> {
                    flushBuffer()
                    yield(component)
                }
            }
        }
        flushBuffer()
    }
}
