package processm.conformance.models.alignments.cache

import io.mockk.mockk
import processm.conformance.models.alignments.Alignment
import processm.conformance.models.alignments.events.EventsSummarizer
import processm.core.log.Event
import processm.core.models.commons.ProcessModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class LRUAlignmentCacheTest {


    @Test
    fun `hit count`() {
        val a = ArrayList<Event>()
        val b = ArrayList<Event>()
        val c = ArrayList<Event>()
        val cache = LRUAlignmentCache<Int>(summarizer = EventsSummarizer {
            return@EventsSummarizer when {
                it === a -> 0
                it === b -> 1
                it === c -> 2
                else -> throw IllegalArgumentException()
            }
        })
        val pm = mockk<ProcessModel>()
        val aa = mockk<Alignment>()
        val ab = mockk<Alignment>()
        cache.put(pm, a, aa)
        cache.put(pm, b, ab)
        assertEquals(0, cache.hitCounter)
        assertNull(cache.get(pm, c))
        assertEquals(0, cache.hitCounter)
        assertSame(aa, cache.get(pm, a))
        assertEquals(1, cache.hitCounter)
        assertSame(ab, cache.get(pm, b))
        assertEquals(2, cache.hitCounter)
        assertSame(ab, cache.get(pm, b))
        assertEquals(3, cache.hitCounter)
    }

    @Test
    fun `remove old`() {
        val a = ArrayList<Event>()
        val b = ArrayList<Event>()
        val c = ArrayList<Event>()
        val cache = LRUAlignmentCache<Int>(summarizer = EventsSummarizer {
            return@EventsSummarizer when {
                it === a -> 0
                it === b -> 1
                it === c -> 2
                else -> throw IllegalArgumentException()
            }
        }, 2)
        val pm = mockk<ProcessModel>()
        val aa = mockk<Alignment>()
        val ab = mockk<Alignment>()
        val ac = mockk<Alignment>()
        cache.put(pm, a, aa)
        cache.put(pm, b, ab)
        assertNull(cache.get(pm, c))
        assertSame(aa, cache.get(pm, a))
        assertSame(ab, cache.get(pm, b))
        cache.put(pm, c, ac)
        assertNull(cache.get(pm, a))
        assertSame(ac, cache.get(pm, c))
        assertSame(ab, cache.get(pm, b))
    }
}