package processm.conformance.models.alignments.events

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.extension.ExtendWith
import processm.core.log.Event
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import kotlin.test.*

@ExtendWith(MockKExtension::class)
class EventsSummarizerTest {

    @MockK
    lateinit var e1: Event

    @MockK
    lateinit var e2: Event

    @MockK
    lateinit var e3: Event

    @MockK
    lateinit var t1a: Trace

    @MockK
    lateinit var t1b: Trace

    @MockK
    lateinit var t2: Trace

    @MockK
    lateinit var t3: Trace


    @MockK
    lateinit var block: (Trace) -> Int

    @MockK
    lateinit var nullableBlock: (Trace) -> Int?

    lateinit var summarizer: EventsSummarizer<Int>

    @BeforeTest
    fun setup() {
        every { t1a.events } returns sequenceOf(e1)
        every { t1b.events } returns sequenceOf(e1)
        every { t2.events } returns sequenceOf(e2)
        every { t3.events } returns sequenceOf(e3)
        summarizer = EventsSummarizer() {
            when {
                it[0] === e1 -> 1
                it[0] === e2 -> 2
                it[0] === e3 -> 3
                else -> error(it)
            }
        }
        every { block(match { it.events.first() === e1 }) } returns 1
        every { block(match { it.events.first() === e2 }) } returns 2
        every { block(match { it.events.first() === e3 }) } returns 3
        every { nullableBlock(match { it.events.first() === e1 }) } returns null
        every { nullableBlock(match { it.events.first() === e2 }) } returns 2
        every { nullableBlock(match { it.events.first() === e3 }) } returns 3
    }

    @Test
    fun `test eager flatMap for different traces`() {
        val result = summarizer.flatMap(listOf(t1a, t2, t3), block)
        assertEquals(listOf(1, 2, 3), result)
        verify(exactly = 1) { block(t1a) }
        verify(exactly = 0) { block(t1b) }
        verify(exactly = 1) { block(t2) }
        verify(exactly = 1) { block(t3) }
    }

    @Test
    fun `test eager flatMap for repeated traces`() {
        val result = summarizer.flatMap(listOf(t1a, t2, t1b), block)
        assertEquals(listOf(1, 2, 1), result)
        verify(exactly = 1) { block(t1a) }
        verify(exactly = 1) { block(t2) }
        verify(exactly = 0) { block(t1b) }
        verify(exactly = 0) { block(t3) }
    }

    @Test
    fun `test eager flatMap for repeated traces and nulls`() {
        val result = summarizer.flatMap(listOf(t1a, t2, t1b), nullableBlock)
        assertEquals(listOf(null, 2, null), result)
        verify(exactly = 1) { nullableBlock(t1a) }
        verify(exactly = 0) { nullableBlock(t1b) }
        verify(exactly = 1) { nullableBlock(t2) }
        verify(exactly = 0) { nullableBlock(t3) }
    }

    @Test
    fun `test lazy flatMap for repeated traces and nulls`() {
        val seq = summarizer.flatMap(sequenceOf(t1a, t2, t1b), nullableBlock).iterator()
        verify(exactly = 0) { nullableBlock(t1a) }
        verify(exactly = 0) { nullableBlock(t1b) }
        verify(exactly = 0) { nullableBlock(t2) }
        verify(exactly = 0) { nullableBlock(t3) }
        assertNull(seq.next())
        verify(exactly = 1) { nullableBlock(t1a) }
        verify(exactly = 0) { nullableBlock(t1b) }
        verify(exactly = 0) { nullableBlock(t2) }
        verify(exactly = 0) { nullableBlock(t3) }
        assertEquals(2, seq.next())
        verify(exactly = 1) { nullableBlock(t1a) }
        verify(exactly = 0) { nullableBlock(t1b) }
        verify(exactly = 1) { nullableBlock(t2) }
        verify(exactly = 0) { nullableBlock(t3) }
        assertNull(seq.next())
        verify(exactly = 1) { nullableBlock(t1a) }
        verify(exactly = 0) { nullableBlock(t1b) }
        verify(exactly = 1) { nullableBlock(t2) }
        verify(exactly = 0) { nullableBlock(t3) }
        assertFalse { seq.hasNext() }
    }

    @Test
    fun `test lazy flatMap for Log for repeated traces and nulls`() {
        val log = mockk<Log>()
        every { log.traces } returns sequenceOf(t1a, t2, t1b)
        val seq = summarizer.flatMap(log, nullableBlock).iterator()
        verify(exactly = 0) { nullableBlock(t1a) }
        verify(exactly = 0) { nullableBlock(t1b) }
        verify(exactly = 0) { nullableBlock(t2) }
        verify(exactly = 0) { nullableBlock(t3) }
        assertNull(seq.next())
        verify(exactly = 1) { nullableBlock(t1a) }
        verify(exactly = 0) { nullableBlock(t1b) }
        verify(exactly = 0) { nullableBlock(t2) }
        verify(exactly = 0) { nullableBlock(t3) }
        assertEquals(2, seq.next())
        verify(exactly = 1) { nullableBlock(t1a) }
        verify(exactly = 0) { nullableBlock(t1b) }
        verify(exactly = 1) { nullableBlock(t2) }
        verify(exactly = 0) { nullableBlock(t3) }
        assertNull(seq.next())
        verify(exactly = 1) { nullableBlock(t1a) }
        verify(exactly = 0) { nullableBlock(t1b) }
        verify(exactly = 1) { nullableBlock(t2) }
        verify(exactly = 0) { nullableBlock(t3) }
        assertFalse { seq.hasNext() }
    }
}