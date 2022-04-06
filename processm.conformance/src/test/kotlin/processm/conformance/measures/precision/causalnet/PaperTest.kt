package processm.conformance.measures.precision.causalnet

import processm.core.log.Helpers
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.models.causalnet.*

/**
 * Unless otherwise noted all models and values are from [1]
 *
 * [1] van der Aalst, W., Adriansyah, A. and van Dongen, B. (2012), Replaying history on process models for conformance
 * checking and performance analysis. WIREs Data Mining Knowl Discov, 2: 182-192. https://doi.org/10.1002/widm.1045
 */
abstract class PaperTest {

    protected val a = Node("a")
    protected val b = Node("b")
    protected val c = Node("c")
    protected val d = Node("d")
    protected val e = Node("e")
    protected val f = Node("f")
    protected val g = Node("g")
    protected val h = Node("h")

    protected val model1 = causalnet {
        start splits a
        a splits (b + d) or (c + d)
        b splits e
        c splits e
        d splits e
        e splits g or h or f
        g splits end
        h splits end
        f splits (b + d) or (c + d)
        start joins a
        a or f join b
        a or f join c
        a or f join d
        b + d or c + d join e
        e joins g
        e joins h
        e joins f
        g joins end
        h joins end
    }

    protected val model2 = causalnet {
        start splits a
        a splits c
        c splits d
        d splits e
        e splits h
        h splits end
        start joins a
        a joins c
        c joins d
        d joins e
        e joins h
        h joins end
    }

    protected val model3: CausalNet

    init {
        model3 = causalnet {
            start splits a
            a splits b or c or d or e or f
            b splits c or d or e or f or g or h
            c splits b or d or e or f or g or h
            d splits b or c or e or f or g or h
            e splits b or c or d or f or g or h
            f splits b or c or d or e or g or h
            g splits end
            h splits end
            start joins a
            a or c or d or e or f join b
            a or b or d or e or f join c
            a or b or c or e or f join d
            a or b or c or d or f join e
            a or b or c or d or e join f
            f or b or c or d or e join g
            f or b or c or d or e join h
            g joins end
            h joins end
        }
    }


    protected val log = Log(
        sequence {
            yieldAll(Helpers.trace(a, c, d, e, h) * 455)
            yieldAll(Helpers.trace(a, b, d, e, g) * 191)
            yieldAll(Helpers.trace(a, d, c, e, h) * 177)
            yieldAll(Helpers.trace(a, b, d, e, h) * 144)
            yieldAll(Helpers.trace(a, c, d, e, g) * 111)
            yieldAll(Helpers.trace(a, d, c, e, g) * 82)
            yieldAll(Helpers.trace(a, d, b, e, h) * 56)
            yieldAll(Helpers.trace(a, c, d, e, f, d, b, e, h) * 47)
            yieldAll(Helpers.trace(a, d, b, e, g) * 38)
            yieldAll(Helpers.trace(a, c, d, e, f, b, d, e, h) * 33)
            yieldAll(Helpers.trace(a, c, d, e, f, b, d, e, g) * 14)
            yieldAll(Helpers.trace(a, c, d, e, f, d, b, e, g) * 11)
            yieldAll(Helpers.trace(a, d, c, e, f, c, d, e, h) * 9)
            yieldAll(Helpers.trace(a, d, c, e, f, d, b, e, h) * 8)
            yieldAll(Helpers.trace(a, d, c, e, f, b, d, e, g) * 5)
            yieldAll(Helpers.trace(a, c, d, e, f, b, d, e, f, d, b, e, g) * 3)
            yieldAll(Helpers.trace(a, d, c, e, f, d, b, e, g) * 2)
            yieldAll(Helpers.trace(a, d, c, e, f, b, d, e, f, b, d, e, g) * 2)
            yield(Helpers.trace(a, d, c, e, f, d, b, e, f, b, d, e, h))
            yield(Helpers.trace(a, d, b, e, f, b, d, e, e, f, d, b, e, g))
            yield(Helpers.trace(a, d, c, e, f, d, b, e, f, c, d, e, f, d, b, e, g))
        }.map { Trace(sequenceOf(Helpers.event("start")) + it.events + sequenceOf(Helpers.event("end"))) }
    )


    protected val logs = Log(
        sequence {
            yield(Helpers.trace(a, c, d, e, h))
            yield(Helpers.trace(a, b, d, e, g))
            yield(Helpers.trace(a, d, c, e, h))
            yield(Helpers.trace(a, b, d, e, h))
            yield(Helpers.trace(a, c, d, e, g))
            yield(Helpers.trace(a, d, c, e, g))
            yield(Helpers.trace(a, d, b, e, h))
            yield(Helpers.trace(a, c, d, e, f, d, b, e, h))
            yield(Helpers.trace(a, d, b, e, g))
            yield(Helpers.trace(a, c, d, e, f, b, d, e, h))
            yield(Helpers.trace(a, c, d, e, f, b, d, e, g))
            yield(Helpers.trace(a, c, d, e, f, d, b, e, g))
            yield(Helpers.trace(a, d, c, e, f, c, d, e, h))
            yield(Helpers.trace(a, d, c, e, f, d, b, e, h))
            yield(Helpers.trace(a, d, c, e, f, b, d, e, g))
            yield(Helpers.trace(a, c, d, e, f, b, d, e, f, d, b, e, g))
            yield(Helpers.trace(a, d, c, e, f, d, b, e, g))
            yield(Helpers.trace(a, d, c, e, f, b, d, e, f, b, d, e, g))
            yield(Helpers.trace(a, d, c, e, f, d, b, e, f, b, d, e, h))
            yield(Helpers.trace(a, d, b, e, f, b, d, e, e, f, d, b, e, g))
            yield(Helpers.trace(a, d, c, e, f, d, b, e, f, c, d, e, f, d, b, e, g))
        }.map { Trace(sequenceOf(Helpers.event("start")) + it.events + sequenceOf(Helpers.event("end"))) }
    )

    protected val model4: CausalNet

    init {
        val m = MutableCausalNet()
        for ((tidx, trace) in logs.traces.withIndex()) {
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

    protected val model1Precision = 0.97
    protected val model2Precision = 1.0
    protected val model3Precision = 0.471 // 0.471 is computed by CNetPerfectPrecision rather than from the paper. The paper gives 0.41 there, but both model representation and precision definition are different
    protected val model4Precision = 1.0
}