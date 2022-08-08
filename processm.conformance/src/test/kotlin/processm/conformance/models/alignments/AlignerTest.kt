package processm.conformance.models.alignments

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.jupiter.api.extension.ExtendWith
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.models.commons.ProcessModel
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@ExtendWith(MockKExtension::class)
class AlignerTest {

    @MockK
    lateinit var t1: Trace

    @MockK
    lateinit var t2: Trace

    @MockK
    lateinit var t3: Trace

    @MockK
    lateinit var a1: Alignment

    @MockK
    lateinit var a2: Alignment

    @MockK
    lateinit var a3: Alignment

    lateinit var aligner: Aligner

    @BeforeTest
    fun setup() {
        every { t1.events } returns sequenceOf(mockk("e1"))
        every { t2.events } returns sequenceOf(mockk("e2"))
        every { t3.events } returns sequenceOf(mockk("e3"))
        aligner = spyk(object : Aligner {
            override val penalty: PenaltyFunction
                get() = error("")
            override val model: ProcessModel
                get() = error("")

            override fun align(trace: Trace, costUpperBound: Int): Alignment? =
                when {
                    trace === t1 -> a1
                    trace === t2 -> a2
                    trace === t3 -> a3
                    else -> error(trace)
                }
        })
    }

    @Test
    fun `test align without summarizer`() {
        val result = aligner.align(sequenceOf(t1, t2, t3, t1), null).iterator()
        verify(exactly = 0) { aligner.align(t1) }
        verify(exactly = 0) { aligner.align(t2) }
        verify(exactly = 0) { aligner.align(t3) }
        assertEquals(a1, result.next())
        verify(exactly = 1) { aligner.align(t1) }
        verify(exactly = 0) { aligner.align(t2) }
        verify(exactly = 0) { aligner.align(t3) }
        assertEquals(a2, result.next())
        verify(exactly = 1) { aligner.align(t1) }
        verify(exactly = 1) { aligner.align(t2) }
        verify(exactly = 0) { aligner.align(t3) }
        assertEquals(a3, result.next())
        verify(exactly = 1) { aligner.align(t1) }
        verify(exactly = 1) { aligner.align(t2) }
        verify(exactly = 1) { aligner.align(t3) }
        assertEquals(a1, result.next())
        verify(exactly = 2) { aligner.align(t1) }
        verify(exactly = 1) { aligner.align(t2) }
        verify(exactly = 1) { aligner.align(t3) }
        assertFalse { result.hasNext() }
    }

    @Test
    fun `test align with default summarizer`() {
        val result = aligner.align(sequenceOf(t1, t2, t3, t1)).iterator()
        verify(exactly = 0) { aligner.align(t1) }
        verify(exactly = 0) { aligner.align(t2) }
        verify(exactly = 0) { aligner.align(t3) }
        assertEquals(a1, result.next())
        verify(exactly = 1) { aligner.align(t1) }
        verify(exactly = 0) { aligner.align(t2) }
        verify(exactly = 0) { aligner.align(t3) }
        assertEquals(a2, result.next())
        verify(exactly = 1) { aligner.align(t1) }
        verify(exactly = 1) { aligner.align(t2) }
        verify(exactly = 0) { aligner.align(t3) }
        assertEquals(a3, result.next())
        verify(exactly = 1) { aligner.align(t1) }
        verify(exactly = 1) { aligner.align(t2) }
        verify(exactly = 1) { aligner.align(t3) }
        assertEquals(a1, result.next())
        verify(exactly = 1) { aligner.align(t1) }
        verify(exactly = 1) { aligner.align(t2) }
        verify(exactly = 1) { aligner.align(t3) }
        assertFalse { result.hasNext() }
    }

    @Test
    fun `test align for log without summarizer`() {
        val log = mockk<Log>()
        every { log.traces } returns sequenceOf(t1, t2, t3)
        val result = aligner.align(log, null).iterator()
        verify(exactly = 0) { aligner.align(t1) }
        verify(exactly = 0) { aligner.align(t2) }
        verify(exactly = 0) { aligner.align(t3) }
        assertEquals(a1, result.next())
        verify(exactly = 1) { aligner.align(t1) }
        verify(exactly = 0) { aligner.align(t2) }
        verify(exactly = 0) { aligner.align(t3) }
        assertEquals(a2, result.next())
        verify(exactly = 1) { aligner.align(t1) }
        verify(exactly = 1) { aligner.align(t2) }
        verify(exactly = 0) { aligner.align(t3) }
        assertEquals(a3, result.next())
        verify(exactly = 1) { aligner.align(t1) }
        verify(exactly = 1) { aligner.align(t2) }
        verify(exactly = 1) { aligner.align(t3) }
        assertFalse { result.hasNext() }
    }
}
