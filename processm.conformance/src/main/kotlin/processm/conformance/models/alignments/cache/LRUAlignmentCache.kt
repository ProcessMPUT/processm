package processm.conformance.models.alignments.cache

import processm.conformance.models.alignments.Alignment
import processm.conformance.models.alignments.events.EventsSummarizer
import processm.core.log.Event
import processm.core.models.commons.ProcessModel

/**
 * LRU (Least-recently used) cache for alignments, to be used with [CachingAligner]
 */
open class LRUAlignmentCache<EventsSummary>(val summarizer: EventsSummarizer<EventsSummary>, val maxSize: Int = 65535) :
    AlignmentCache {

    protected val cache = object : LinkedHashMap<Pair<ProcessModel, EventsSummary>, Alignment>(maxSize, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<ProcessModel, EventsSummary>, Alignment>?): Boolean {
            return this.size > maxSize
        }
    }

    var hitCounter: Int = 0
        private set

    protected fun cacheKey(model: ProcessModel, events: List<Event>) = model to summarizer(events)

    @Synchronized
    override fun get(model: ProcessModel, events: List<Event>): Alignment? = cache[cacheKey(model, events)].also {
        if (it !== null)
            hitCounter++
    }

    @Synchronized
    override fun put(model: ProcessModel, events: List<Event>, alignment: Alignment) {
        cache[cacheKey(model, events)] = alignment
    }

}
