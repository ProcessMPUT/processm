package processm.conformance.models.alignments.cache

import processm.conformance.models.alignments.events.DefaultEventsSummarizer
import processm.core.log.Event

class DefaultAlignmentCache(maxSize: Int = 65535) :
    LRUAlignmentCache<List<Event>>(DefaultEventsSummarizer(), maxSize)