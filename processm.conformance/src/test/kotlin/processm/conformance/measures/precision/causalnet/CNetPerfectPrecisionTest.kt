package processm.conformance.measures.precision.causalnet

import processm.conformance.measures.precision.AbstractPrecision
import processm.core.helpers.HashMapWithDefault
import processm.core.helpers.Trie
import processm.core.log.Helpers.assertDoubleEquals
import processm.core.log.Helpers.logFromModel
import processm.core.log.Helpers.trace
import processm.core.log.hierarchical.Log
import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.causalnet
import processm.core.models.commons.Activity
import processm.core.verifiers.causalnet.CausalNetVerifierImpl
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals


class CNetPerfectPrecisionTest {

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
        val pa = CNetPerfectPrecision(dodReference)(log)
        assertDoubleEquals(1.0, pa)
    }

    private fun testPossible(model: CausalNet, maxSeqLen: Int = Int.MAX_VALUE, maxPrefixLen: Int = Int.MAX_VALUE) {
        val validSequences = CausalNetVerifierImpl(model)
            .computeSetOfValidSequences(false) { it, _ -> it.size < maxSeqLen }
            .map { it.mapNotNull { if (!it.a.isSilent) it.a else null } }.toList()
        val prefix2possible = HashMapWithDefault<List<Node>, HashSet<Node>>() { HashSet() }
        val trie = Trie<Activity, AbstractPrecision.PrecisionData> { AbstractPrecision.PrecisionData(0, 0, 0) }
        for (seq in validSequences) {
            var current = trie
            for (i in 0 until min(seq.size, maxPrefixLen)) {
                prefix2possible[seq.subList(0, i)].add(seq[i])
                current = current.getOrPut(seq[i])
            }
        }
        val pa = CNetPerfectPrecision(model)
        pa.availableActivities(trie)
        for ((prefix, expected) in prefix2possible.entries) {
            val actual = trie.getOrPut(prefix).value.available
            assertEquals(expected.size, actual, "prefix=$prefix expected=$expected actual=$actual")
        }
    }

    @Test
    fun possible1() {
        val model = causalnet {
            start splits a + b or a + c or b + c
            a splits d or e
            b splits d or f
            c splits e or f
            d splits end
            e splits end
            f splits end
            start joins a
            start joins b
            start joins c
            a + b join d
            a + c join e
            b + c join f
            d or e or f join end
        }
        testPossible(model)
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
        val trie = Trie<Activity, AbstractPrecision.PrecisionData> { AbstractPrecision.PrecisionData(0, 0, 0) }
        trie.getOrPut(listOf(s, a, a, c, b, b))
        val pa = CNetPerfectPrecision(model)
        pa.availableActivities(trie)
        assertEquals(1, trie.getOrPut(listOf(s)).value.available)
        assertEquals(2, trie.getOrPut(listOf(s, a)).value.available)
        assertEquals(2, trie.getOrPut(listOf(s, a, a)).value.available)
        assertEquals(1, trie.getOrPut(listOf(s, a, a, c)).value.available)
        assertEquals(1, trie.getOrPut(listOf(s, a, a, c, b)).value.available)
        assertEquals(1, trie.getOrPut(listOf(s, a, a, c, b, b)).value.available)
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

        val trie = Trie<Activity, AbstractPrecision.PrecisionData> { AbstractPrecision.PrecisionData(0, 0, 0) }
        trie.getOrPut(listOf(a, a, c, b, b))
        val pa = CNetPerfectPrecision(model)
        pa.availableActivities(trie)
        assertEquals(1, trie.value.available)
        assertEquals(2, trie.getOrPut(listOf(a)).value.available)
        assertEquals(2, trie.getOrPut(listOf(a, a)).value.available)
        assertEquals(1, trie.getOrPut(listOf(a, a, c)).value.available)
        assertEquals(1, trie.getOrPut(listOf(a, a, c, b)).value.available)
        assertEquals(0, trie.getOrPut(listOf(a, a, c, b, b)).value.available)
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
        val pa = CNetPerfectPrecision(diamond1)(Log(traces))
        assertDoubleEquals((1.0 + 1.0 / 2 + 1.0 + 1.0) / 4.0, pa, 0.001)
    }

    @Test
    fun diamond1SecondTrace() {
        val traces = sequenceOf(trace(a, c, b, d))
        val pa = CNetPerfectPrecision(diamond1)(Log(traces))
        assertDoubleEquals((1.0 + 1.0 + 1.0 / 2 + 1.0) / 4.0, pa, 0.001)
    }

    @Test
    fun diamond1BothTraces() {
        val traces = sequenceOf(trace(a, b, c, d), trace(a, c, b, d))
        val pa = CNetPerfectPrecision(diamond1)(Log(traces))
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
        val traces = sequenceOf(trace(a, b, c, d))
        val pa = CNetPerfectPrecision(diamond2)(Log(traces))
        assertDoubleEquals((1.0 + 1.0 / 2 + 1.0 + 1.0) / 4.0, pa, 0.001)
    }

    @Test
    fun diamond2SecondTrace() {
        val traces = sequenceOf(trace(a, c, b, d))
        val pa = CNetPerfectPrecision(diamond2)(Log(traces))
        assertDoubleEquals((1.0 + 1.0 + 1.0 / 2 + 1.0) / 4.0, pa, 0.001)
    }

    @Test
    fun diamond2BothTraces() {
        val traces = sequenceOf(trace(a, b, c, d), trace(a, c, b, d))
        val pa = CNetPerfectPrecision(diamond2)(Log(traces))
        assertDoubleEquals(1.0, pa, 0.001)
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
        val pa = CNetPerfectPrecision(diamond3)(Log(traces))
        assertDoubleEquals((1.0 + 1.0 / 2 + 1.0 / 2 + 1.0) / 4.0, pa, 0.001)
    }

    @Test
    fun diamond3acbd() {
        val traces = sequenceOf(trace(a, c, b, d))
        val pa = CNetPerfectPrecision(diamond3)(Log(traces))
        assertDoubleEquals((1.0 + 1.0 / 2 + 1.0 / 2 + 1.0) / 4.0, pa, 0.001)
    }

    @Test
    fun diamond3ab() {
        val traces = sequenceOf(trace(a, b, c, d), trace(a, b, d))
        val pa = CNetPerfectPrecision(diamond3)(Log(traces))
        assertDoubleEquals(((1.0 + 1.0 / 2 + 1.0 + 1.0) + (1.0 + 1.0 / 2 + 1.0)) / 7, pa, 0.001)
    }

    @Test
    fun diamond3ac() {
        val traces = sequenceOf(trace(a, c, b, d), trace(a, c, d))
        val pa = CNetPerfectPrecision(diamond3)(Log(traces))
        assertDoubleEquals(((1.0 + 1.0 / 2 + 1.0 + 1.0) + (1.0 + 1.0 / 2 + 1.0)) / 7, pa, 0.001)
    }

    @Test
    fun diamond3bd() {
        val traces = sequenceOf(trace(a, c, b, d), trace(a, b, d))
        val pa = CNetPerfectPrecision(diamond3)(Log(traces))
        assertDoubleEquals(((1.0 + 1.0 + 1.0 / 2 + 1.0) + (1.0 + 1.0 / 2 + 1.0)) / 7, pa, 0.001)
    }

    @Test
    fun diamond3cd() {
        val traces = sequenceOf(trace(a, b, c, d), trace(a, c, d))
        val pa = CNetPerfectPrecision(diamond3)(Log(traces))
        assertDoubleEquals(((1.0 + 1.0 + 1.0 / 2 + 1.0) + (1.0 + 1.0 / 2 + 1.0)) / 7, pa, 0.001)
    }

    @Test
    fun diamond3a_d() {
        val traces = sequenceOf(trace(a, b, d), trace(a, c, d))
        val pa = CNetPerfectPrecision(diamond3)(Log(traces))
        assertDoubleEquals(((1.0 + 1.0 + 1.0 / 2) * 2) / 6, pa, 0.001)
    }

    @Test
    fun diamond3AllTraces() {
        val traces = sequenceOf(trace(a, b, c, d), trace(a, c, b, d), trace(a, b, d), trace(a, c, d))
        val pa = CNetPerfectPrecision(diamond3)(Log(traces))
        assertDoubleEquals(1.0, pa, 0.001)
    }
}