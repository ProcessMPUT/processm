package processm.conformance.measures.precision.causalnet

import processm.core.log.hierarchical.Trace
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.assertTrue


operator fun Trace.times(n: Int): Sequence<Trace> = sequence {
    for (i in 0 until n)
        yield(this@times)
}


// http://realtimecollisiondetection.net/blog/?p=89
fun assertDoubleEquals(expected: Double, actual: Double, prec: Double = 1e-3) =
    assertTrue(
        abs(expected - actual) <= prec * max(max(1.0, abs(expected)), abs(actual)),
        "Expected: $expected, actual: $actual, prec: $prec"
    )