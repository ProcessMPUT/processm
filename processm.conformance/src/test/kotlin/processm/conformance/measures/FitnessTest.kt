package processm.conformance.measures

import processm.conformance.PetriNets.fig32
import processm.conformance.PetriNets.fig624N3
import processm.conformance.PetriNets.sequence
import processm.conformance.models.alignments.CompositeAligner
import processm.core.log.Helpers.assertDoubleEquals
import processm.core.log.Helpers.event
import processm.core.log.Helpers.times
import processm.core.log.Helpers.trace
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.models.causalnet.*
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests based on "Replaying History on Process Models for Conformance Checking and Performance Analysis" (DOI 10.1002/widm.1045
)
 */
class FitnessTest {

    private val a = Node("a")
    private val b = Node("b")
    private val c = Node("c")
    private val d = Node("d")
    private val e = Node("e")
    private val f = Node("f")
    private val g = Node("g")
    private val h = Node("h")


    private val log = Log(
        sequence {
            yieldAll(trace(a, c, d, e, h) * 455)
            yieldAll(trace(a, b, d, e, g) * 191)
            yieldAll(trace(a, d, c, e, h) * 177)
            yieldAll(trace(a, b, d, e, h) * 144)
            yieldAll(trace(a, c, d, e, g) * 111)
            yieldAll(trace(a, d, c, e, g) * 82)
            yieldAll(trace(a, d, b, e, h) * 56)
            yieldAll(trace(a, c, d, e, f, d, b, e, h) * 47)
            yieldAll(trace(a, d, b, e, g) * 38)
            yieldAll(trace(a, c, d, e, f, b, d, e, h) * 33)
            yieldAll(trace(a, c, d, e, f, b, d, e, g) * 14)
            yieldAll(trace(a, c, d, e, f, d, b, e, g) * 11)
            yieldAll(trace(a, d, c, e, f, c, d, e, h) * 9)
            yieldAll(trace(a, d, c, e, f, d, b, e, h) * 8)
            yieldAll(trace(a, d, c, e, f, b, d, e, g) * 5)
            yieldAll(trace(a, c, d, e, f, b, d, e, f, d, b, e, g) * 3)
            yieldAll(trace(a, d, c, e, f, d, b, e, g) * 2)
            yieldAll(trace(a, d, c, e, f, b, d, e, f, b, d, e, g) * 2)
            yield(trace(a, d, c, e, f, d, b, e, f, b, d, e, h))
            yield(trace(a, d, b, e, f, b, d, e, e, f, d, b, e, g))
            yield(trace(a, d, c, e, f, d, b, e, f, c, d, e, f, d, b, e, g))
        }
    )

    private val uniqueTraces =
        sequence {
            yield(trace(a, c, d, e, h))
            yield(trace(a, b, d, e, g))
            yield(trace(a, d, c, e, h))
            yield(trace(a, b, d, e, h))
            yield(trace(a, c, d, e, g))
            yield(trace(a, d, c, e, g))
            yield(trace(a, d, b, e, h))
            yield(trace(a, c, d, e, f, d, b, e, h))
            yield(trace(a, d, b, e, g))
            yield(trace(a, c, d, e, f, b, d, e, h))
            yield(trace(a, c, d, e, f, b, d, e, g))
            yield(trace(a, c, d, e, f, d, b, e, g))
            yield(trace(a, d, c, e, f, c, d, e, h))
            yield(trace(a, d, c, e, f, d, b, e, h))
            yield(trace(a, d, c, e, f, b, d, e, g))
            yield(trace(a, c, d, e, f, b, d, e, f, d, b, e, g))
            yield(trace(a, d, c, e, f, d, b, e, g))
            yield(trace(a, d, c, e, f, b, d, e, f, b, d, e, g))
            yield(trace(a, d, c, e, f, d, b, e, f, b, d, e, h))
            yield(trace(a, d, b, e, f, b, d, e, e, f, d, b, e, g))
            yield(trace(a, d, c, e, f, d, b, e, f, c, d, e, f, d, b, e, g))
        }.map { Trace(sequenceOf(event("start")) + it.events + sequenceOf(event("end"))) }


    private val model4: CausalNet

    init {
        val m = MutableCausalNet()
        for ((tidx, trace) in uniqueTraces.withIndex()) {
            val n = trace.events.count()
            val nodes = listOf(m.start) + trace.events.filterIndexed { eidx, e -> eidx in 1 until n - 1 }
                .mapIndexed { eidx, e -> Node(e.conceptName!!, "$tidx/$eidx") }.toList() + listOf(m.end)
            m.addInstance(*nodes.toTypedArray())
            for (i in 0 until nodes.size - 1) {
                val src = nodes[i]
                val dst = nodes[i + 1]
                val d = m.addDependency(src, dst)
                m.addSplit(Split(setOf(d)))
                m.addJoin(Join(setOf(d)))
            }
        }
        model4 = m
    }

    @Test
    fun `model1 movem`() {
        assertEquals(5, Fitness(CompositeAligner(fig32)).movem)
    }

    @Test
    fun `model1 fitness`() {
        assertDoubleEquals(1.0, Fitness(CompositeAligner(fig32))(log))
    }

    @Test
    fun `model2 fitness`() {
        assertDoubleEquals(0.8, Fitness(CompositeAligner(sequence))(log))
    }

    @Test
    fun `model3 fitness`() {
        assertDoubleEquals(1.0, Fitness(CompositeAligner(fig624N3))(log))
    }

    @Test
    fun `model4 fitness`() {
        assertDoubleEquals(1.0, Fitness(CompositeAligner(model4))(log))
    }

    @Test
    fun `model1 incomplete alignment`() {
        val alignments = List(log.traces.count()) { null }
        val f = Fitness(CompositeAligner(fig32))
        assertEquals(0.0, f(log, alignments))
    }

    @Test
    fun `model1 partial alignment`() {
        val alignments = List(log.traces.count()) { null }
        val f = Fitness(CompositeAligner(fig32))
        assertEquals(0.0, f(log, alignments))
    }
}
