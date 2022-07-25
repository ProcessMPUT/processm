package processm.conformance.models.alignments.cache

import processm.conformance.models.alignments.Alignment
import processm.core.log.Event
import processm.core.models.commons.ProcessModel

/**
 * A cache from (ProcessModel, Trace) to Alignment. Intended usage is primarily with multiple decomposition aligners
 * operating on similar models and thus having shared decompositions.
 *
 * There's no [getOrPut] or similar method on purpose, because ensuring that it is thread-safe and efficient could be a tricky business.
 *
 * Note that the general contract on [List.hashCode] and [List.equals] requires use of the contained items.
 */
interface AlignmentCache {
    fun get(model: ProcessModel, events: List<Event>): Alignment?
    fun put(model: ProcessModel, events: List<Event>, alignment: Alignment)
}
