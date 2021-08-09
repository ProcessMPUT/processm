package processm.experimental.performance

import kotlin.test.Test
import kotlin.test.assertEquals

class HelpersTest {

    @Test
    fun setCoveringTest() {
        val universe = List(14) { it }.toSet()
        val subsets = listOf(
            setOf(0, 7),
            setOf(1, 2, 8, 9),
            setOf(3, 4, 5, 6, 10, 11, 12, 13),
            setOf(0, 1, 2, 3, 4, 5, 6),
            setOf(7, 8, 9, 10, 11, 12, 13)
        )
        assertEquals(
            setOf(3, 4),
            setCovering(universe, subsets).toSet()
        )
    }

    @Test
    fun notReallySetCoveringTest() {
        val universe = setOf(7, 9)
        val subsets = listOf(
            setOf(0, 7),
            setOf(1, 2, 8, 9),
            setOf(3, 4, 5, 6, 10, 11, 12, 13),
            setOf(0, 1, 2, 3, 4, 5, 6),
            setOf(7, 8, 9, 10, 11, 12, 13)
        )
        assertEquals(
            setOf(0, 1),
            notReallySetCovering(universe, subsets).toSet()
        )
    }

    @Test
    fun notReallySetCoveringTest2() {
        val universe = setOf(1, 2)
        val subsets = listOf(sortedSetOf(2), sortedSetOf(2, 1))
        assertEquals(
            listOf(1),
            notReallySetCovering(universe, subsets)
        )
    }

    @Test
    fun notReallySetCoveringTest3() {
        val universe = setOf(1, 2)
        val subsets = listOf(sortedSetOf(3, 2), sortedSetOf(2, 1))
        assertEquals(
            listOf(1),
            notReallySetCovering(universe, subsets)
        )
    }

    @Test
    fun test1() {
        println(
            separateDisjointFamilies(
                listOf(
                    setOf(1),
                    setOf(2),
                    setOf(3),
                    setOf(1, 3),
                    setOf(2, 4),
                    setOf(4)
                )
            )
        )
    }
}
