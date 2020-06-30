package processm.experimental.performance

import ch.qos.logback.classic.Level
import io.mockk.every
import io.mockk.mockk
import org.slf4j.LoggerFactory.getLogger
import processm.core.helpers.mapToSet
import processm.core.log.Event
import processm.core.log.XMLXESInputStream
import processm.core.log.hierarchical.HoneyBadgerHierarchicalXESInputStream
import processm.core.log.hierarchical.InMemoryXESProcessing
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.models.causalnet.*
import processm.experimental.onlinehmpaper.filterLog
import processm.miners.heuristicminer.OfflineHeuristicMiner
import processm.miners.heuristicminer.bindingproviders.BestFirstBindingProvider
import processm.miners.heuristicminer.longdistance.VoidLongDistanceDependencyMiner
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.*

@InMemoryXESProcessing
class PerformanceAnalyzerTest {

    private val a = Node("a")
    private val b = Node("b")
    private val c = Node("c")
    private val d = Node("d")
    private val e = Node("e")
    private val f = Node("f")
    private val g = Node("g")
    private val h = Node("h")

    private fun event(name: String): Event {
        return object :Event() {
            override var conceptName: String? = name
        }
        /*
        val e = mockk<Event>()
        every { e.conceptName } returns name
        every { e.lifecycleTransition } returns null
        return e

         */
    }

    private fun trace(vararg nodes: Node): Trace =
        Trace(nodes.asList().map { event(it.name) }.asSequence())

    private fun assertAlignmentEquals(expectedCost: Double, expected: List<Pair<Node?, Node?>>, actual: Alignment) {
        assertEquals(expectedCost, actual.cost)
        assertEquals(
            expected.map { it.first?.name to it.second },
            actual.alignment.map { it.event?.conceptName to it.activity })
    }

    @BeforeTest
    fun setupLogger() {
//        (getLogger("processm.experimental") as ch.qos.logback.classic.Logger).level = Level.TRACE
    }

    private val model0 = causalnet {
        start = a
        end = e
        a splits b
        b splits c + d
        c splits e
        d splits e
        a joins b
        b joins c
        b joins d
        c + d join e
    }

    private val emptyLog = Log(emptySequence())

    @Test
    fun `model0 perfect`() {
        val alignment = PerformanceAnalyzer(emptyLog, model0).computeOptimalAlignment(trace(a, b, c, d, e)).first
        assertAlignmentEquals(0.0, listOf(a to a, b to b, c to c, d to d, e to e), alignment)
    }

    @Test
    fun `model0 skip model`() {
        val alignment = PerformanceAnalyzer(emptyLog, model0).computeOptimalAlignment(trace(a, c, d, e)).first
        assertAlignmentEquals(1.0, listOf(a to a, null to b, c to c, d to d, e to e), alignment)
    }

    @Test
    fun `model0 skip log`() {
        val alignment = PerformanceAnalyzer(emptyLog, model0).computeOptimalAlignment(trace(a, e, b, c, d, e)).first
        assertAlignmentEquals(1.0, listOf(a to a, e to null, b to b, c to c, d to d, e to e), alignment)
    }

    @Test
    fun `model0 skip log and model`() {
        val alignment = PerformanceAnalyzer(emptyLog, model0).computeOptimalAlignment(trace(a, e, c, d, e)).first
        assertAlignmentEquals(2.0, listOf(a to a, null to b, e to null, c to c, d to d, e to e), alignment)
    }

    @Test
    fun `model0 skip everything`() {
        val alignment = PerformanceAnalyzer(emptyLog, model0).computeOptimalAlignment(trace(f, f, f, f)).first
        assertAlignmentEquals(
            9.0,
            listOf(null to a, f to null, null to b, f to null, null to d, null to c, null to e, f to null, f to null),
            alignment
        )
    }

    @Test
    fun `model0 movem`() {
        assertEquals(5.0, PerformanceAnalyzer(emptyLog, model0).movem)
    }


    /**
     * From "Replaying History on Process Models for Conformance Checking and Performance Analysis"
     */
    private val model1 = causalnet {
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

    private val model2 = causalnet {
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

    private val model3: CausalNet

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

    @Test
    fun `model1 movem`() {
        assertEquals(7.0, PerformanceAnalyzer(emptyLog, model1).movem)
    }

    private operator fun Trace.times(n: Int): Sequence<Trace> = sequence {
        for (i in 0 until n)
            yield(this@times)
    }

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
        }.map { Trace(sequenceOf(event("start")) + it.events + sequenceOf(event("end"))) }
    )


    private val logs = Log(
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
    )

    private val model4:CausalNet

    init {
        val m = MutableCausalNet()
        for((tidx, trace) in logs.traces.withIndex())  {
            val n =trace.events.count()
            val nodes = listOf(m.start) + trace.events.filterIndexed {eidx, e -> eidx in 1 until n-1}.mapIndexed { eidx, e ->  Node(e.conceptName!!, "$tidx/$eidx") }.toList() + listOf(m.end)
            m.addInstance(*nodes.toTypedArray())
            for(i in 0 until nodes.size-1) {
                val src=nodes[i]
                val dst = nodes[i+1]
                val d= m.addDependency(src, dst)
                m.addSplit(Split(setOf(d)))
                m.addJoin(Join(setOf(d)))
            }
        }
        model4 = m
    }

    // http://realtimecollisiondetection.net/blog/?p=89
    private fun assertDoubleEquals(expected: Double, actual: Double, prec: Double = 1e-3) =
        assertTrue(
            abs(expected - actual) <= prec * max(max(1.0, abs(expected)), abs(actual)),
            "Expected: $expected, actual: $actual, prec: $prec"
        )


    @Test
    fun `model1 fitness`() {
        assertDoubleEquals(1.0, PerformanceAnalyzer(log, model1).fitness)
    }

    @Test
    fun `model1 precision`() {
        // The paper gives 0.97 here, but both model representation and precision definition are different
        assertDoubleEquals(0.912, PerformanceAnalyzer(log, model1).precision)
    }

    @Test
    fun `model1 event level generalization full log`() {
        assertDoubleEquals(1.0, PerformanceAnalyzer(log, model1).eventLevelGeneralization, 0.01)
    }

    @Test
    fun `model1 state level generalization full log`() {
        assertDoubleEquals(1.0, PerformanceAnalyzer(log, model1).stateLevelGeneralization, 0.01)
    }

    @Test
    fun `model2 precision`() {
        assertDoubleEquals(1.0, PerformanceAnalyzer(log, model2).precision)
    }

    @Test
    fun `model2 fitness`() {
        // paper offers 0.8 here, but they don't count start and end
        // On the other hand the actual value is of lesser importance, the important thing is that it stays the same over time
        assertDoubleEquals(0.856, PerformanceAnalyzer(log, model2).fitness)
    }

    @Test
    fun `model2 event level generalization full log`() {
        assertDoubleEquals(1.0, PerformanceAnalyzer(log, model2).eventLevelGeneralization, 0.01)
    }

    @Test
    fun `model2 state level generalization full log`() {
        assertDoubleEquals(1.0, PerformanceAnalyzer(log, model2).stateLevelGeneralization, 0.01)
    }

    @Test
    fun `model3 precision`() {
        // The paper gives 0.41 here, but both model representation and precision definition are different
        assertDoubleEquals(0.613, PerformanceAnalyzer(log, model3).precision)
    }

    @Test
    fun `model3 event level generalization full log`() {
        assertDoubleEquals(1.0, PerformanceAnalyzer(log, model3).eventLevelGeneralization, 0.01)
    }

    @Test
    fun `model3 state level generalization full log`() {
        assertDoubleEquals(0.95, PerformanceAnalyzer(log, model3).stateLevelGeneralization, 0.01)
    }

    @Test
    fun `model4 fitness`() {
        assertDoubleEquals(1.0, PerformanceAnalyzer(log, model4).fitness)
    }

    @Test
    fun `model4 precision`() {
        assertDoubleEquals(1.0, PerformanceAnalyzer(log, model4).precision)
    }

    @Test
    fun `model4 event level generalization full log`() {
        assertDoubleEquals(0.99, PerformanceAnalyzer(log, model4).eventLevelGeneralization, 0.01)
    }

    @Test
    fun `model4 state level generalization full log`() {
        assertDoubleEquals(0.61, PerformanceAnalyzer(log, model4).stateLevelGeneralization, 0.01)
    }

    @Test
    fun generalization() {
        val pa1=PerformanceAnalyzer(logs, model1)
        val pa2=PerformanceAnalyzer(logs, model2)
        val pa3=PerformanceAnalyzer(logs, model3)
        val pa4=PerformanceAnalyzer(logs, model4)
        println("state: g1=${pa1.stateLevelGeneralization} g2=${pa2.stateLevelGeneralization} g3=${pa3.stateLevelGeneralization} g4=${pa4.stateLevelGeneralization}")
        println("event: g1=${pa1.eventLevelGeneralization} g2=${pa2.eventLevelGeneralization} g3=${pa3.eventLevelGeneralization} g4=${pa4.eventLevelGeneralization}")
//        assertTrue(
//            g2 <= g1 && g1 <= g3,
//            "M3 is at least as general as M1, which is at least as general as M2, but g1=$g1 g2=$g2 g3=$g3"
//        )
    }

    private val model5 = causalnet {
        start = a
        end = e
        a splits b or c or b + c
        b splits d
        c splits d
        d splits e
        a joins b
        a joins c
        b or c or b + c join d
        d joins e
    }

    @Test
    fun test() {
        for (alignment in PerformanceAnalyzer(emptyLog, model3).allFreePartialAlignments(listOf(model3.start, a, b))) {
            println(alignment.state.mapToSet { it.target })
        }
    }

    private val model6 = causalnet {
        start splits a
        start joins a
        a splits b
        b splits c + d
        c splits e
        d splits e
        a joins b
        b joins c
        b joins d
        c + d join e
        e splits end
        e joins end
    }

    @Test
    fun `model6 alignment without start and end`() {
        val alignment = PerformanceAnalyzer(emptyLog, model6).computeOptimalAlignment(trace(a,b,c,d,e)).first
        assertAlignmentEquals(
            2.0,
            listOf(null to model6.start, a  to a , b to b, c to c, d to d, e to e, null to model6.end),
            alignment
        )
    }

    @Test
    fun `model6 alignment ignoring start and end`() {
        val alignment = PerformanceAnalyzer(emptyLog, model6, SkipSpecialForFree(StandardDistance())).computeOptimalAlignment(trace(a,b,c,d,e)).first
        assertAlignmentEquals(
            0.0,
            listOf(null to model6.start, a  to a , b to b, c to c, d to d, e to e, null to model6.end),
            alignment
        )
    }

    @Test
    fun `nongreedy alignment`() {
        val model7 = causalnet {
            start =a
            end =e
            a splits b or c
            b splits e
            c splits d
            d splits e
            a joins b
            a joins c
            c joins d
            b or d join e
        }
        val alignment = PerformanceAnalyzer(emptyLog, model7).computeOptimalAlignment(trace(a,b,e,c,d,e)).first
        assertAlignmentEquals(2.0,
            listOf(a to a, b to null, e to null, c to c, d to d, e to e),
            alignment
        )
    }

    @Test
    fun `deferred prize`() {
        val model = causalnet {
            start =a
            end =e
            a splits b+f or b+g or b+h
            b splits c
            c splits d
            d splits e
            f splits e
            g splits e
            h splits e
            a joins b
            b joins c
            c joins d
            d+f or d+g or d+h join e
            a joins f
            a joins g
            a joins h
        }
        val alignments = listOf(f,g,h).associateWith{ PerformanceAnalyzer(emptyLog, model).computeOptimalAlignment(trace(a,b,c,d,it,e)) }
        val length = alignments.values.map { it.second }
        println(length)
        assertTrue { length.max()!! <= 7 }
    }

    /*
    @Test
    fun `model1`() {
        val alignment = PerformanceAnalyzer(model1).optimalAlignment(trace(model1.start, a, b, e, f, b, h, model1.end))
        assertAlignmentEquals(3.0,
            listOf(model1.start to model1.start, a to a, b to b, null to d, e to e, f to f, b to b, null to d, null to e, h to h, model1.end to model1.end),
            alignment
        )
    }
     */

    private fun load(logfile: String): Log {
        File(logfile).inputStream().use { base ->
            return HoneyBadgerHierarchicalXESInputStream(XMLXESInputStream(GZIPInputStream(base))).first()
        }
    }

    @Ignore
    @Test
    fun `BPIC15_2`() {
        val log=load("../xes-logs/BPIC15_2.xes.gz")
        val offline = OfflineHeuristicMiner(bindingProvider = BestFirstBindingProvider(maxQueueSize = 100), longDistanceDependencyMiner = VoidLongDistanceDependencyMiner())
        offline.processLog(log)
        println(offline.result)
        val pa = PerformanceAnalyzer(log, offline.result)
        println(pa.fitness)
        println(pa.precision)
    }

    @Test
    fun `CoSeLoG_WABO_2`() {
        val log=load("../xes-logs/CoSeLoG_WABO_2.xes.gz")
        val offline = OfflineHeuristicMiner(bindingProvider = BestFirstBindingProvider(maxQueueSize = 100), longDistanceDependencyMiner = VoidLongDistanceDependencyMiner())
        offline.processLog(log)
        println(offline.result)
        println()
        println(offline.result.splits.values.flatten().filter { it.dependencies.size >= 2 })
        println(offline.result.joins.values.flatten().filter { it.dependencies.size >= 2 })
        /*
        val pa = PerformanceAnalyzer(log, offline.result, SkipSpecialForFree(StandardDistance()))
        for((i, a) in pa.optimalAlignment.withIndex())
            println("$i -> ${a.cost}")
        println(pa.fcost)
        println(pa.fitness)
        println(pa.precision)
         */
    }

    @Ignore
    @Test
    fun `bpi_challenge_2017`() {
        val log=load("../xes-logs/bpi_challenge_2017.xes.gz")
        val offline = OfflineHeuristicMiner(bindingProvider = BestFirstBindingProvider(maxQueueSize = 100), longDistanceDependencyMiner = VoidLongDistanceDependencyMiner())
        offline.processLog(log)
        println(offline.result)
    }


    @Test
    fun `nasa-cev-complete-splitted`() {
        val fulllog=load("../xes-logs/nasa-cev-complete-splitted.xes.gz")
        val log = filterLog(fulllog)
        val offline = OfflineHeuristicMiner(bindingProvider = BestFirstBindingProvider(maxQueueSize = 100), longDistanceDependencyMiner = VoidLongDistanceDependencyMiner())
        offline.processLog(log)
        println(offline.result)
        val pa = PerformanceAnalyzer(log, offline.result, SkipSpecialForFree(StandardDistance()))
        val bot="⊥"
//        for((i, a) in pa.optimalAlignment.withIndex()) {
//            println("$i -> ${a.cost}")
////            for(step in a.alignment)
////                println("\t${step.event?.conceptName?:bot} -> ${step.activity?:bot}")
//        }
        println(pa.fitness)
        println(pa.precision)
    }

}