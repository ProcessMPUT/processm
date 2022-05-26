package processm.conformance.measures.precision.causalnet

import processm.core.log.hierarchical.Trace


operator fun Trace.times(n: Int): Sequence<Trace> = sequence {
    for (i in 0 until n)
        yield(this@times)
}