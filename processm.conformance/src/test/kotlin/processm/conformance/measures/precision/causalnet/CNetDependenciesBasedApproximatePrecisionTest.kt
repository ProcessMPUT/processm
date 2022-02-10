package processm.conformance.measures.precision.causalnet

import processm.core.log.Helpers.logFromModel
import processm.core.log.Helpers.trace
import processm.core.log.hierarchical.Log
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.causalnet
import kotlin.test.Test
import kotlin.test.assertEquals


class CNetDependenciesBasedApproximatePrecisionTest {

    private val a = Node("a")
    private val b = Node("b")
    private val c = Node("c")
    private val d = Node("d")
    private val e = Node("e")
    private val f = Node("f")

    @Test
    fun `diamond of diamonds`() {
        val a = Node("a")
        val b1 = Node("b1")
        val c1 = Node("c1")
        val d1 = Node("d1")
        val e1 = Node("e1")
        val b2 = Node("b2")
        val c2 = Node("c2")
        val d2 = Node("d2")
        val e2 = Node("e2")
        val f = Node("f")
        val dodReference = causalnet {
            start = a
            end = f
            a splits b1 + b2
            b1 splits c1 + d1
            b2 splits c2 + d2
            c1 splits e1
            d1 splits e1
            c2 splits e2
            d2 splits e2
            e1 splits f
            e2 splits f
            a joins b1
            a joins b2
            b1 joins c1
            b1 joins d1
            b2 joins c2
            b2 joins d2
            c1 + d1 join e1
            c2 + d2 join e2
            e1 + e2 join f
        }
        val log = logFromModel(dodReference)
        val pa = CNetDependenciesBasedApproximatePrecision(dodReference)(log)
        //the expected value was computed by CNetDependenciesBasedApproximatePrecisionAux itself and is intended only as a regression test
        assertDoubleEquals(0.735, pa)
    }


    @Test
    fun possible2a() {
        val nstart = Node("start")
        val nend = Node("end")
        val model = causalnet {
            start = nstart
            end = nend
            start splits a
            a splits a + b or c
            b splits b or end
            c splits b
            start or a join a
            c or a + b join b
            a joins c
            b joins end
        }
        println(model)
        val s = model.start
        val pa = CNetDependenciesBasedApproximatePrecision(model)
        assertEquals(setOf(a), pa.availableActivities(listOf(s)))
        assertEquals(setOf(a, b, c), pa.availableActivities(listOf(s, a)))
        assertEquals(setOf(a, b, c), pa.availableActivities(listOf(s, a, a)))
        assertEquals(setOf(a, b, c), pa.availableActivities(listOf(s, a, a, c)))
        assertEquals(
            setOf(a, b, c, nend),
            pa.availableActivities(listOf(s, a, a, c, b))
        ) // a; a,b,c; a,b,c; b; b, end - a,a,c,b -> a,b,c,end
        assertEquals(
            setOf(a, b, c, nend),
            pa.availableActivities(listOf(s, a, a, c, b, b))
        )// a; a,b,c; a,b,c; b; b, end; b, end - a,a,c,b,b -> a,b,c,end
    }

    @Test
    fun possible2b() {
        val model = causalnet {
            start splits a
            a splits a + b or c
            b splits b or end
            c splits b
            start or a join a
            c or a + b join b
            a joins c
            b joins end
        }
        val pa = CNetDependenciesBasedApproximatePrecision(model)
        assertEquals(setOf(a), pa.availableActivities(emptyList()))
        assertEquals(setOf(a, b, c), pa.availableActivities(listOf(a)))
        assertEquals(setOf(a, b, c), pa.availableActivities(listOf(a, a)))
        assertEquals(setOf(a, b, c), pa.availableActivities(listOf(a, a, c)))
        assertEquals(setOf(a, b, c), pa.availableActivities(listOf(a, a, c, b)))
        assertEquals(setOf(a, b, c), pa.availableActivities(listOf(a, a, c, b, b)))
    }

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
        val traces = sequenceOf(trace(a, b, c, d))
        val pa = CNetDependenciesBasedApproximatePrecision(diamond1)(Log(traces))
        assertDoubleEquals((1.0 + 1.0 / 2 + 1.0 / 2 + 1.0) / 4.0, pa, 0.001)
    }

    @Test
    fun diamond1SecondTrace() {
        val traces = sequenceOf(trace(a, c, b, d))
        val pa = CNetDependenciesBasedApproximatePrecision(diamond1)(Log(traces))
        assertDoubleEquals((1.0 + 1.0 / 2 + 1.0 / 2 + 1.0) / 4.0, pa, 0.001)
    }

    @Test
    fun diamond1BothTraces() {
        val traces = sequenceOf(trace(a, b, c, d), trace(a, c, b, d))
        val pa = CNetDependenciesBasedApproximatePrecision(diamond1)(Log(traces))
        assertDoubleEquals(2 * (1.0 + 1.0 + 1.0 / 2 + 1.0) / 8.0, pa, 0.001)
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
        val traces = sequenceOf(trace(a, b, c, d))
        val pa = CNetDependenciesBasedApproximatePrecision(diamond2)(Log(traces))
        assertDoubleEquals((1.0 + 1.0 / 2 + 1.0 / 2 + 1.0) / 4.0, pa, 0.001)
    }

    @Test
    fun diamond2SecondTrace() {
        val traces = sequenceOf(trace(a, c, b, d))
        val pa = CNetDependenciesBasedApproximatePrecision(diamond2)(Log(traces))
        assertDoubleEquals((1.0 + 1.0 / 2 + 1.0 / 2 + 1.0) / 4.0, pa, 0.001)
    }

    @Test
    fun diamond2BothTraces() {
        val traces = sequenceOf(trace(a, b, c, d), trace(a, c, b, d))
        val pa = CNetDependenciesBasedApproximatePrecision(diamond2)(Log(traces))
        assertDoubleEquals(2 * (1.0 + 1.0 + 1.0 / 2 + 1.0) / 8.0, pa, 0.001)
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
        val traces = sequenceOf(trace(a, b, c, d))
        val pa = CNetDependenciesBasedApproximatePrecision(diamond3)(Log(traces))
        assertDoubleEquals((1.0 + 1.0 / 2 + 1.0 / 2 + 1.0) / 4.0, pa, 0.001)
    }

    @Test
    fun diamond3acbd() {
        val traces = sequenceOf(trace(a, c, b, d))
        val pa = CNetDependenciesBasedApproximatePrecision(diamond3)(Log(traces))
        assertDoubleEquals((1.0 + 1.0 / 2 + 1.0 / 2 + 1.0) / 4.0, pa, 0.001)
    }

    @Test
    fun diamond3ab() {
        val traces = sequenceOf(trace(a, b, c, d), trace(a, b, d))
        val pa = CNetDependenciesBasedApproximatePrecision(diamond3)(Log(traces))
        assertDoubleEquals(((1.0 + 1.0 / 2 + 1.0 + 1.0) + (1.0 + 1.0 / 2 + 1.0)) / 7, pa, 0.001)
    }

    @Test
    fun diamond3ac() {
        val traces = sequenceOf(trace(a, c, b, d), trace(a, c, d))
        val pa = CNetDependenciesBasedApproximatePrecision(diamond3)(Log(traces))
        assertDoubleEquals(((1.0 + 1.0 / 2 + 1.0 + 1.0) + (1.0 + 1.0 / 2 + 1.0)) / 7, pa, 0.001)
    }

    @Test
    fun diamond3bd() {
        val traces = sequenceOf(trace(a, c, b, d), trace(a, b, d))
        val pa = CNetDependenciesBasedApproximatePrecision(diamond3)(Log(traces))
        assertDoubleEquals(((1.0 + 1.0 + 1.0 / 2 + 1.0) + (1.0 + 1.0 / 2 + 1.0)) / 7, pa, 0.001)
    }

    @Test
    fun diamond3cd() {
        val traces = sequenceOf(trace(a, b, c, d), trace(a, c, d))
        val pa = CNetDependenciesBasedApproximatePrecision(diamond3)(Log(traces))
        assertDoubleEquals(((1.0 + 1.0 + 1.0 / 2 + 1.0) + (1.0 + 1.0 / 2 + 1.0)) / 7, pa, 0.001)
    }

    @Test
    fun diamond3a_d() {
        val traces = sequenceOf(trace(a, b, d), trace(a, c, d))
        val pa = CNetDependenciesBasedApproximatePrecision(diamond3)(Log(traces))
        assertDoubleEquals(((1.0 + 1.0 + 1.0 / 2) * 2) / 6, pa, 0.001)
    }

    @Test
    fun diamond3AllTraces() {
        val traces = sequenceOf(trace(a, b, c, d), trace(a, c, b, d), trace(a, b, d), trace(a, c, d))
        val pa = CNetDependenciesBasedApproximatePrecision(diamond3)(Log(traces))
        assertDoubleEquals(1.0, pa, 0.001)
    }
}