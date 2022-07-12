package processm.miners.causalnet.onlineminer

import org.apache.commons.math3.fraction.BigFraction
import processm.core.helpers.stats.Distribution
import kotlin.math.pow
import kotlin.math.sqrt
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
        System.gc()
        val naiveTime = Distribution((1..nReps).map {
            measureNanoTime {
                var expected = BigFraction.ZERO
                for (v in values)
                    expected += BigFraction(1, v)
            }.toDouble()
        })

        val sumOfReciprocalsTime = Distribution((1..nReps).map {
            measureNanoTime {
                sumOfReciprocals(values)
            }.toDouble()
        })

        // perform one-tailed unpaired t-test for the means of two populations
        // see https://en.wikipedia.org/wiki/Student%27s_t-test#Independent_two-sample_t-test
        // H0: avg(naiveTime) = avg(sumOfRep...)
        // H1: avg(naiveTime) > avg(sumOfRep...)
        // alpha = 0.05
        // df = 2 * nReps - 2
        val sp = sqrt(0.5 * (naiveTime.standardDeviation.pow(2) + sumOfReciprocalsTime.standardDeviation.pow(2)))
        val t = (sumOfReciprocalsTime.average - naiveTime.average) / (sp * sqrt(2.0 / nReps))
        val df = 2 * nReps - 2
        assertEquals(198, df) // this must be consistent with nReps above, as we use tcrit from tables
        val tcrit = 1.652585784

        if (t > 0)
            println(t)
        assertTrue("sumOfReciprocalsTime: $sumOfReciprocalsTime; naiveTime: $naiveTime") { t <= tcrit }
    }
}
