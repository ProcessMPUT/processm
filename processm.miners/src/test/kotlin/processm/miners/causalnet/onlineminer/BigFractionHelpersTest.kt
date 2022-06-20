package processm.miners.causalnet.onlineminer

import org.apache.commons.math3.fraction.BigFraction
import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BigFractionHelpersTest {

    fun test(values: List<Int>) {
        var expected = BigFraction.ZERO
        for (v in values)
            expected += BigFraction(1, v)
        val actual = sumOfReciprocals(values)
        assertEquals(expected, actual)
    }

    @Test
    fun test1() = test(listOf(3, 5, 7))

    @Test
    fun test2() = test(listOf(3))

    @Test
    fun test3() = test(listOf(2, 4, 6, 8))

    @Test
    fun performance() {
        val values = List(30) { 1 shl it }
        val nReps = 100
        val naiveTime = (0..nReps).sumOf {
            measureNanoTime {
                var expected = BigFraction.ZERO
                for (v in values)
                    expected += BigFraction(1, v)
            }
        }
        val sumOfReciprocalsTime = (0..nReps).sumOf {
            measureNanoTime {
                sumOfReciprocals(values)
            }
        }
        assertTrue { sumOfReciprocalsTime <= naiveTime }
    }
}
