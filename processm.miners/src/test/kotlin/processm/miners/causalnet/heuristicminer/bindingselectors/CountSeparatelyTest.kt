package processm.miners.causalnet.heuristicminer.bindingselectors

import io.mockk.mockk
import processm.core.models.causalnet.Split
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CountSeparatelyTest {

    @Test
    fun test() {
        val s1 = mockk<Split>()
        val s2 = mockk<Split>()
        val s3 = mockk<Split>()
        val s4 = mockk<Split>()
        val bs = CountSeparately<Split>(2)
        bs.add(listOf(s1, s2))
        assertTrue { bs.best.isEmpty() }
        bs.add(listOf(s1, s2, s4))
        assertEquals(setOf(s1, s2), bs.best.toSet())
        bs.add(listOf(s1, s2, s3, s3, s3, s3))
        assertEquals(setOf(s1, s2, s3), bs.best.toSet())
        bs.reset()
        assertTrue { bs.best.isEmpty() }
    }
}