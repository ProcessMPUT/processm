package processm.miners.heuristicminer.windowing

import org.apache.commons.lang3.math.Fraction
import org.junit.jupiter.api.TestFactory
import kotlin.system.measureNanoTime
import kotlin.test.*

class HelpersTest {

    fun test(values:List<Int>) {
        var expected = Fraction.ZERO
        for(v in values)
            expected += Fraction.getFraction(1, v)
        val actual = sumInverse(values)
        assertEquals(expected, actual)
    }

    @Test
    fun test1() = test(listOf(3,5,7))

    @Test
    fun test2() = test(listOf(3))

    @Test
    fun test3() = test(listOf(2,4,6,8))

    @Test
    fun performance() {
        val values= List(30) {1 shl it}
        val nReps= 100
        val fractions = (0..nReps).map { measureNanoTime {
            var expected = Fraction.ZERO
            for (v in values)
                expected += Fraction.getFraction(1, v)
        }}.sum()
        val sumInverse = (0..nReps).map { measureNanoTime {
            sumInverse(values) } }.sum()
        println("fractions=$fractions ns\tsumInverse = $sumInverse ns")
    }
}