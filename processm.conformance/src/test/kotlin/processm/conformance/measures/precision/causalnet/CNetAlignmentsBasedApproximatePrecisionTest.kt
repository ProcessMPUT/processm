package processm.conformance.measures.precision.causalnet

import processm.core.log.Helpers
import processm.core.log.Helpers.assertDoubleEquals
import processm.core.log.hierarchical.Log
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.causalnet
import kotlin.test.Test

class CNetAlignmentsBasedApproximatePrecisionTest {

    private val a = Node("a")
    private val b = Node("b")
    private val c = Node("c")
    private val d = Node("d")
    private val e = Node("e")
    private val f = Node("f")

    private val diamond1 = causalnet {
        start = a
        end = d
        a splits b + c
        b splits d
        c splits d
        a joins b
        a joins c
        b + c join d
    }

    @Test
    fun diamond1FirstTrace() {
        val traces = sequenceOf(Helpers.trace(a, b, c, d))
        val pa = CNetAlignmentsBasedApproximatePrecision(diamond1)(Log(traces))
        assertDoubleEquals((1.0 + 1.0 / 2 + 1.0  + 1.0) / 4.0, pa, 0.001)
    }

    @Test
    fun diamond1SecondTrace() {
        val traces = sequenceOf(Helpers.trace(a, c, b, d))
        val pa = CNetAlignmentsBasedApproximatePrecision(diamond1)(Log(traces))
        assertDoubleEquals((1.0 + 1.0 / 2 + 1.0  + 1.0) / 4.0, pa, 0.001)
    }

    @Test
    fun diamond1BothTraces() {
        val traces = sequenceOf(Helpers.trace(a, b, c, d), Helpers.trace(a, c, b, d))
        val pa = CNetAlignmentsBasedApproximatePrecision(diamond1)(Log(traces))
        assertDoubleEquals(1.0, pa, 0.001)
    }


    private val diamond2 = causalnet {
        start splits a
        start joins a
        d splits end
        d joins end

        a splits b + c
        b splits d
        c splits d
        a joins b
        a joins c
        b + c join d
    }

    @Test
    fun diamond2FirstTrace() {
        val traces = sequenceOf(Helpers.trace(a, b, c, d))
        val pa = CNetAlignmentsBasedApproximatePrecision(diamond2)(Log(traces))
        assertDoubleEquals((1.0 + 1.0 / 2 + 1.0  + 1.0) / 4.0, pa, 0.001)
    }

    @Test
    fun diamond2SecondTrace() {
        val traces = sequenceOf(Helpers.trace(a, c, b, d))
        val pa = CNetAlignmentsBasedApproximatePrecision(diamond2)(Log(traces))
        assertDoubleEquals((1.0 + 1.0 / 2 + 1.0 + 1.0) / 4.0, pa, 0.001)
    }

    @Test
    fun diamond2BothTraces() {
        val traces = sequenceOf(Helpers.trace(a, b, c, d), Helpers.trace(a, c, b, d))
        val pa = CNetAlignmentsBasedApproximatePrecision(diamond2)(Log(traces))
        assertDoubleEquals(2 * (1.0 + 1.0 + 1.0 + 1.0) / 8.0, pa, 0.001)
    }


    private val diamond3 = causalnet {
        start = a
        end = d
        a splits b + c or b or c
        b splits d
        c splits d
        a joins b
        a joins c
        b + c or b or c join d
    }

    @Test
    fun diamond3abcd() {
        val traces = sequenceOf(Helpers.trace(a, b, c, d))
        val pa = CNetAlignmentsBasedApproximatePrecision(diamond3)(Log(traces))
        assertDoubleEquals((1.0 + 1.0 / 2 + 1.0 / 2 + 1.0) / 4.0, pa, 0.001)
    }

    @Test
    fun diamond3acbd() {
        val traces = sequenceOf(Helpers.trace(a, c, b, d))
        val pa = CNetAlignmentsBasedApproximatePrecision(diamond3)(Log(traces))
        assertDoubleEquals((1.0 + 1.0 / 2 + 1.0 / 2 + 1.0) / 4.0, pa, 0.001)
    }

    @Test
    fun diamond3ab() {
        val traces = sequenceOf(Helpers.trace(a, b, c, d), Helpers.trace(a, b, d))
        val pa = CNetAlignmentsBasedApproximatePrecision(diamond3)(Log(traces))
        assertDoubleEquals(((1.0 + 1.0 / 2 + 1.0 + 1.0) + (1.0 + 1.0 / 2 + 1.0)) / 7, pa, 0.001)
    }

    @Test
    fun diamond3ac() {
        val traces = sequenceOf(Helpers.trace(a, c, b, d), Helpers.trace(a, c, d))
        val pa = CNetAlignmentsBasedApproximatePrecision(diamond3)(Log(traces))
        assertDoubleEquals(((1.0 + 1.0 / 2 + 1.0 + 1.0) + (1.0 + 1.0 / 2 + 1.0)) / 7, pa, 0.001)
    }

    @Test
    fun diamond3bd() {
        val traces = sequenceOf(Helpers.trace(a, c, b, d), Helpers.trace(a, b, d))
        val pa = CNetAlignmentsBasedApproximatePrecision(diamond3)(Log(traces))
        assertDoubleEquals(((1.0 + 1.0 + 1.0 / 2 + 1.0) + (1.0 + 1.0 + 1.0)) / 7, pa, 0.001)
    }

    @Test
    fun diamond3cd() {
        val traces = sequenceOf(Helpers.trace(a, b, c, d), Helpers.trace(a, c, d))
        val pa = CNetAlignmentsBasedApproximatePrecision(diamond3)(Log(traces))
        assertDoubleEquals(((1.0 + 1.0 + 1.0 / 2 + 1.0) + (1.0 + 1.0  + 1.0)) / 7, pa, 0.001)
    }

    @Test
    fun diamond3a_d() {
        val traces = sequenceOf(Helpers.trace(a, b, d), Helpers.trace(a, c, d))
        val pa = CNetAlignmentsBasedApproximatePrecision(diamond3)(Log(traces))
        assertDoubleEquals(((1.0 + 1.0 + 1.0 ) * 2) / 6, pa, 0.001)
    }

    @Test
    fun diamond3AllTraces() {
        val traces = sequenceOf(
            Helpers.trace(a, b, c, d),
            Helpers.trace(a, c, b, d),
            Helpers.trace(a, b, d),
            Helpers.trace(a, c, d)
        )
        val pa = CNetAlignmentsBasedApproximatePrecision(diamond3)(Log(traces))
        assertDoubleEquals(1.0, pa, 0.001)
    }
}
