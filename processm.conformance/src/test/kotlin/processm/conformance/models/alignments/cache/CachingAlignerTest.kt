package processm.conformance.models.alignments.cache

import io.mockk.*
import processm.conformance.models.alignments.Aligner
import processm.conformance.models.alignments.Alignment
import processm.core.log.hierarchical.Trace
import processm.core.models.commons.ProcessModel
import kotlin.test.Test
import kotlin.test.assertSame

class CachingAlignerTest {

    @Test
    fun test() {
        val trace = mockk<Trace>(relaxed = true)
        val alignment = mockk<Alignment>()
        val pm = mockk<ProcessModel>()
        val baseAligner = mockk<Aligner>()
        every { baseAligner.model } returns pm
        every { baseAligner.align(trace, Int.MAX_VALUE) } returns alignment andThenThrows RuntimeException()

        val alignmentCache = mockk<AlignmentCache>()
        every { alignmentCache.get(pm, trace.events.toList()) } returns null andThen alignment
        every { alignmentCache.put(pm, trace.events.toList(), alignment) } just Runs

        val cachingAligner = CachingAligner(baseAligner, alignmentCache)

        verify(exactly = 0) { baseAligner.align(any() as Trace, Int.MAX_VALUE) }
        assertSame(alignment, cachingAligner.align(trace))
        verify(exactly = 1) { baseAligner.align(any() as Trace, Int.MAX_VALUE) }
        assertSame(alignment, cachingAligner.align(trace))
        verify(exactly = 1) { baseAligner.align(any() as Trace, Int.MAX_VALUE) }
        assertSame(alignment, cachingAligner.align(trace))
        verify(exactly = 1) { baseAligner.align(any() as Trace, Int.MAX_VALUE) }
    }
}
