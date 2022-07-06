package processm.enhancement.simulation

import processm.core.log.hierarchical.toFlatSequence
import processm.enhancement.simulation.Logs.basic
import processm.enhancement.simulation.Logs.sec722
import processm.enhancement.simulation.Logs.table81
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class XESInputStreamExtensionsTest {
    @Test
    fun `directly-follows frequencies for basic log`() {
        val freq = basic.toFlatSequence().getDirectlyFollowsFrequencies()
        assertEquals(null, freq["a", "a"])
        assertEquals(3, freq["a", "b"])
        assertEquals(2, freq["a", "c"])
        assertEquals(null, freq["a", "d"])
        assertEquals(1, freq["a", "e"])

        assertEquals(null, freq["b", "a"])
        assertEquals(null, freq["b", "b"])
        assertEquals(3, freq["b", "c"])
        assertEquals(2, freq["b", "d"])
        assertEquals(null, freq["b", "e"])

        assertEquals(null, freq["c", "a"])
        assertEquals(2, freq["c", "b"])
        assertEquals(null, freq["c", "c"])
        assertEquals(3, freq["c", "d"])
        assertEquals(null, freq["c", "e"])

        assertEquals(null, freq["e", "a"])
        assertEquals(null, freq["e", "b"])
        assertEquals(null, freq["e", "c"])
        assertEquals(1, freq["e", "d"])
        assertEquals(null, freq["e", "e"])
    }

    @Test
    fun `directly-follows conditional probabilities for basic log`() {
        val prob = basic.toFlatSequence().getDirectlyFollowsProbabilities()
        assertEquals(3.0 / 6.0, prob["a", "b"])
        assertEquals(2.0 / 6.0, prob["a", "c"])
        assertEquals(1.0 / 6.0, prob["a", "e"])
        assertEquals(3.0 / 5.0, prob["b", "c"])
        assertEquals(2.0 / 5.0, prob["b", "d"])
        assertEquals(2.0 / 5.0, prob["c", "b"])
        assertEquals(3.0 / 5.0, prob["c", "d"])
        assertEquals(1.0 / 1.0, prob["e", "d"])
    }

    @Test
    fun `directly-follows frequencies for PM book Table 8 1 log`() {
        val freq = table81.toFlatSequence().getDirectlyFollowsFrequencies()
        assertEquals(null, freq["a", "a"])
        assertEquals(191 + 144, freq["a", "b"])
        assertEquals(455 + 111 + 47 + 33 + 14 + 11 + 3, freq["a", "c"])
        assertEquals(177 + 82 + 56 + 38 + 9 + 8 + 5 + 2 + 2 + 1 + 1 + 1, freq["a", "d"])
        assertEquals(null, freq["a", "e"])
        assertEquals(null, freq["a", "f"])
        assertEquals(null, freq["a", "g"])
        assertEquals(null, freq["a", "h"])

        assertEquals(null, freq["b", "a"])
        assertEquals(null, freq["b", "b"])
        assertEquals(null, freq["b", "c"])
        assertEquals(191 + 144 + 33 + 14 + 5 + 3 + 2 * 2 + 1 + 1, freq["b", "d"])
        assertEquals(56 + 47 + 38 + 11 + 8 + 3 + 2 + 1 + 2 * 1 + 2 * 1, freq["b", "e"])
        assertEquals(null, freq["b", "f"])
        assertEquals(null, freq["b", "g"])
        assertEquals(null, freq["b", "h"])

        assertEquals(null, freq["c", "a"])
        assertEquals(null, freq["c", "b"])
        assertEquals(null, freq["c", "c"])
        assertEquals(455 + 111 + 47 + 33 + 14 + 11 + 9 + 3 + 1, freq["c", "d"])
        assertEquals(177 + 82 + 9 + 8 + 5 + 2 + 2 + 1 + 1, freq["c", "e"])
        assertEquals(null, freq["c", "f"])
        assertEquals(null, freq["c", "g"])
        assertEquals(null, freq["c", "h"])

        assertEquals(null, freq["d", "a"])
        assertEquals(56 + 47 + 38 + 11 + 8 + 3 + 2 + 1 + 2 * 1 + 2 * 1, freq["d", "b"])
        assertEquals(177 + 82 + 9 + 8 + 5 + 2 + 2 + 1 + 1, freq["d", "c"])
        assertEquals(null, freq["d", "d"])
        assertEquals(
            455 + 191 + 144 + 111 + 47 + 2 * 33 + 2 * 14 + 11 + 9 + 5 + 2 * 3 + 2 * 2 + 1 + 1 + 1,
            freq["d", "e"]
        )
        assertEquals(null, freq["d", "f"])
        assertEquals(null, freq["d", "g"])
        assertEquals(null, freq["d", "h"])

        assertEquals(null, freq["e", "a"])
        assertEquals(null, freq["e", "b"])
        assertEquals(null, freq["e", "c"])
        assertEquals(null, freq["e", "d"])
        assertEquals(null, freq["e", "e"])
        assertEquals(47 + 33 + 14 + 11 + 9 + 8 + 5 + 2 * 3 + 2 + 2 * 2 + 2 * 1 + 2 * 1 + 3 * 1, freq["e", "f"])
        assertEquals(191 + 111 + 82 + 38 + 14 + 11 + 5 + 3 + 2 + 2 + 1 + 1, freq["e", "g"])
        assertEquals(455 + 177 + 144 + 56 + 47 + 33 + 9 + 8 + 1, freq["e", "h"])

        assertEquals(null, freq["f", "a"])
        assertEquals(33 + 14 + 5 + 3 + 2 * 2 + 1 + 1, freq["f", "b"])
        assertEquals(9 + 1, freq["f", "c"])
        assertEquals(47 + 11 + 8 + 3 + 2 + 1 + 1 + 2 * 1, freq["f", "d"])
        assertEquals(null, freq["f", "e"])
        assertEquals(null, freq["f", "f"])
        assertEquals(null, freq["f", "g"])
        assertEquals(null, freq["f", "h"])

        assertTrue(freq.getRow("g").isEmpty())
        assertTrue(freq.getRow("h").isEmpty())
    }

    @Test
    fun `directly-follows conditional probabilities for PM book Table 8 1 log`() {
        val prob = table81.toFlatSequence().getDirectlyFollowsProbabilities()
        val totalA = 191 + 144 + 455 + 111 + 47 + 33 + 14 + 11 + 3 + 177 + 82 + 56 + 38 + 9 + 8 + 5 + 2 + 2 + 1 + 1 + 1
        assertEquals(null, prob["a", "a"])
        assertEquals((191.0 + 144.0) / totalA, prob["a", "b"])
        assertEquals((455.0 + 111.0 + 47.0 + 33.0 + 14.0 + 11.0 + 3.0) / totalA, prob["a", "c"])
        assertEquals((177.0 + 82.0 + 56.0 + 38.0 + 9.0 + 8.0 + 5.0 + 2.0 + 2.0 + 1 + 1 + 1) / totalA, prob["a", "d"])
        assertEquals(null, prob["a", "e"])
        assertEquals(null, prob["a", "f"])
        assertEquals(null, prob["a", "g"])
        assertEquals(null, prob["a", "h"])

        val totalB = 191 + 144 + 33 + 14 + 5 + 3 + 2 * 2 + 1 + 1 + 56 + 47 + 38 + 11 + 8 + 3 + 2 + 1 + 2 * 1 + 2 * 1
        assertEquals(null, prob["b", "a"])
        assertEquals(null, prob["b", "b"])
        assertEquals(null, prob["b", "c"])
        assertEquals((191.0 + 144.0 + 33.0 + 14.0 + 5.0 + 3.0 + 2 * 2.0 + 1.0 + 1.0) / totalB, prob["b", "d"])
        assertEquals((56.0 + 47.0 + 38.0 + 11.0 + 8.0 + 3.0 + 2.0 + 1.0 + 2 * 1.0 + 2 * 1.0) / totalB, prob["b", "e"])
        assertEquals(null, prob["b", "f"])
        assertEquals(null, prob["b", "g"])
        assertEquals(null, prob["b", "h"])

        val totalC = 455 + 111 + 47 + 33 + 14 + 11 + 9 + 3 + 1 + 177 + 82 + 9 + 8 + 5 + 2 + 2 + 1 + 1
        assertEquals(null, prob["c", "a"])
        assertEquals(null, prob["c", "b"])
        assertEquals(null, prob["c", "c"])
        assertEquals((455.0 + 111.0 + 47.0 + 33.0 + 14.0 + 11.0 + 9.0 + 3.0 + 1.0) / totalC, prob["c", "d"])
        assertEquals((177.0 + 82.0 + 9.0 + 8.0 + 5.0 + 2.0 + 2.0 + 1.0 + 1.0) / totalC, prob["c", "e"])
        assertEquals(null, prob["c", "f"])
        assertEquals(null, prob["c", "g"])
        assertEquals(null, prob["c", "h"])

        val totalD = 56 + 47 + 38 + 11 + 8 + 3 + 2 + 1 + 2 * 1 + 2 * 1 +
                177 + 82 + 9 + 8 + 5 + 2 + 2 + 1 + 1 +
                455 + 191 + 144 + 111 + 47 + 2 * 33 + 2 * 14 + 11 + 9 + 5 + 2 * 3 + 2 * 2 + 1 + 1 + 1
        assertEquals(null, prob["d", "a"])
        assertEquals((56.0 + 47.0 + 38.0 + 11.0 + 8.0 + 3.0 + 2.0 + 1.0 + 2 * 1.0 + 2 * 1.0) / totalD, prob["d", "b"])
        assertEquals((177.0 + 82.0 + 9.0 + 8.0 + 5.0 + 2.0 + 2.0 + 1.0 + 1.0) / totalD, prob["d", "c"])
        assertEquals(null, prob["d", "d"])
        assertEquals(
            (455.0 + 191.0 + 144.0 + 111.0 + 47.0 + 2 * 33.0 + 2 * 14.0 + 11.0 + 9 + 5 + 2 * 3 + 2 * 2 + 1 + 1 + 1) / totalD,
            prob["d", "e"]
        )
        assertEquals(null, prob["d", "f"])
        assertEquals(null, prob["d", "g"])
        assertEquals(null, prob["d", "h"])

        val totalE = 47 + 33 + 14 + 11 + 9 + 8 + 5 + 2 * 3 + 2 + 2 * 2 + 2 * 1 + 2 * 1 + 3 * 1 +
                191 + 111 + 82 + 38 + 14 + 11 + 5 + 3 + 2 + 2 + 1 + 1 +
                455 + 177 + 144 + 56 + 47 + 33 + 9 + 8 + 1
        assertEquals(null, prob["e", "a"])
        assertEquals(null, prob["e", "b"])
        assertEquals(null, prob["e", "c"])
        assertEquals(null, prob["e", "d"])
        assertEquals(null, prob["e", "e"])
        assertEquals(
            (47.0 + 33.0 + 14.0 + 11.0 + 9.0 + 8.0 + 5.0 + 2 * 3.0 + 2.0 + 2 * 2.0 + 2 * 1.0 + 2 * 1.0 + 3 * 1.0) / totalE,
            prob["e", "f"]
        )
        assertEquals(
            (191.0 + 111.0 + 82.0 + 38.0 + 14.0 + 11.0 + 5.0 + 3.0 + 2.0 + 2.0 + 1.0 + 1.0) / totalE,
            prob["e", "g"]
        )
        assertEquals((455.0 + 177.0 + 144.0 + 56.0 + 47.0 + 33.0 + 9.0 + 8.0 + 1.0) / totalE, prob["e", "h"])

        val totalF = 33 + 14 + 5 + 3 + 2 * 2 + 1 + 1 + 9 + 1 + 47 + 11 + 8 + 3 + 2 + 1 + 1 + 2 * 1
        assertEquals(null, prob["f", "a"])
        assertEquals((33.0 + 14.0 + 5.0 + 3.0 + 2 * 2.0 + 1.0 + 1.0) / totalF, prob["f", "b"])
        assertEquals((9.0 + 1.0) / totalF, prob["f", "c"])
        assertEquals((47.0 + 11.0 + 8.0 + 3.0 + 2.0 + 1.0 + 1.0 + 2 * 1.0) / totalF, prob["f", "d"])
        assertEquals(null, prob["f", "e"])
        assertEquals(null, prob["f", "f"])
        assertEquals(null, prob["f", "g"])
        assertEquals(null, prob["f", "h"])

        assertTrue(prob.getRow("g").isEmpty())
        assertTrue(prob.getRow("h").isEmpty())
    }

    @Test
    fun `directly-follows frequencies for PM book Sec 722 log`() {
        val freq = sec722.toFlatSequence().getDirectlyFollowsFrequencies()
        assertEquals(11, freq["a", "b"])
        assertEquals(11, freq["a", "c"])
        assertEquals(13, freq["a", "d"])
        assertEquals(5, freq["a", "e"])

        assertEquals(10, freq["b", "c"])
        assertEquals(11, freq["b", "e"])

        assertEquals(10, freq["c", "b"])
        assertEquals(11, freq["c", "e"])

        assertEquals(4, freq["d", "d"])
        assertEquals(13, freq["d", "e"])
    }

    @Test
    fun `directly-follows conditional probabilities for PM book Sec 722 log`() {
        val prob = sec722.toFlatSequence().getDirectlyFollowsProbabilities()
        assertEquals(11.0 / 40.0, prob["a", "b"])
        assertEquals(11 / 40.0, prob["a", "c"])
        assertEquals(13 / 40.0, prob["a", "d"])
        assertEquals(5 / 40.0, prob["a", "e"])

        assertEquals(10.0 / 21.0, prob["b", "c"])
        assertEquals(11.0 / 21.0, prob["b", "e"])

        assertEquals(10.0 / 21.0, prob["c", "b"])
        assertEquals(11.0 / 21.0, prob["c", "e"])

        assertEquals(4.0 / 17.0, prob["d", "d"])
        assertEquals(13 / 17.0, prob["d", "e"])
    }
}
