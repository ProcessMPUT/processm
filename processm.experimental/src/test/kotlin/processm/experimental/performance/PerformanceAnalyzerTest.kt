package processm.experimental.performance

import ch.qos.logback.classic.Level
import org.junit.jupiter.api.assertThrows
import org.slf4j.LoggerFactory.getLogger
import processm.core.helpers.HashMapWithDefault
import processm.core.helpers.mapToSet
import processm.core.log.Event
import processm.core.log.Helpers.assertDoubleEquals
import processm.core.log.XMLXESInputStream
import processm.core.log.hierarchical.HoneyBadgerHierarchicalXESInputStream
import processm.core.log.hierarchical.InMemoryXESProcessing
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.models.causalnet.*
import processm.core.models.commons.Activity
import processm.core.verifiers.CausalNetVerifier
import processm.core.verifiers.causalnet.CausalNetVerifierImpl
import processm.experimental.onlinehmpaper.filterLog
import processm.experimental.performance.CausalNets.a
import processm.experimental.performance.CausalNets.b
import processm.experimental.performance.CausalNets.c
import processm.experimental.performance.CausalNets.d
import processm.experimental.performance.CausalNets.e
import processm.experimental.performance.CausalNets.f
import processm.experimental.performance.CausalNets.g
import processm.experimental.performance.CausalNets.h
import processm.experimental.performance.CausalNets.model0
import processm.experimental.performance.CausalNets.model1
import processm.experimental.performance.CausalNets.model2
import processm.experimental.performance.CausalNets.model3
import processm.experimental.performance.CausalNets.model6
import processm.experimental.performance.CausalNets.model7
import processm.miners.causalnet.heuristicminer.OfflineHeuristicMiner
import processm.miners.causalnet.heuristicminer.bindingproviders.BestFirstBindingProvider
import processm.miners.causalnet.heuristicminer.longdistance.VoidLongDistanceDependencyMiner
import processm.miners.causalnet.onlineminer.OnlineMiner
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.math.min
import kotlin.test.*

@InMemoryXESProcessing
class PerformanceAnalyzerTest {

    private fun event(name: String): Event {
        return object : Event() {
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
        (getLogger("processm.experimental") as ch.qos.logback.classic.Logger).level = Level.TRACE
    }

    private val emptyLog = Log(emptySequence())

    @Test
    fun `model0 perfect`() {
        val alignment =
            PerformanceAnalyzer(emptyLog, model0).computeOptimalAlignment(trace(a, b, c, d, e), 100).alignment
        assertNotNull(alignment)
        assertAlignmentEquals(0.0, listOf(a to a, b to b, c to c, d to d, e to e), alignment)
    }

    @Test
    fun `model0 skip model`() {
        val alignment = PerformanceAnalyzer(emptyLog, model0).computeOptimalAlignment(trace(a, c, d, e), 100).alignment
        assertNotNull(alignment)
        assertAlignmentEquals(1.0, listOf(a to a, null to b, c to c, d to d, e to e), alignment)
    }

    @Test
    fun `model0 skip log`() {
        val alignment =
            PerformanceAnalyzer(emptyLog, model0).computeOptimalAlignment(trace(a, e, b, c, d, e), 100).alignment
        assertNotNull(alignment)
        assertAlignmentEquals(1.0, listOf(a to a, e to null, b to b, c to c, d to d, e to e), alignment)
    }

    @Test
    fun `model0 skip log and model`() {
        val alignment =
            PerformanceAnalyzer(emptyLog, model0).computeOptimalAlignment(trace(a, e, c, d, e), 100).alignment
        assertNotNull(alignment)
        assertAlignmentEquals(2.0, listOf(a to a, null to b, e to null, c to c, d to d, e to e), alignment)
    }

    @Test
    fun `model0 skip everything`() {
        val alignment = PerformanceAnalyzer(emptyLog, model0).computeOptimalAlignment(trace(f, f, f, f), 100).alignment
        assertNotNull(alignment)
        assertEquals(9.0, alignment.cost)
        val actual = alignment.alignment.map { it.event?.conceptName to it.activity }
        assertEquals(4, actual.count { it == (f.activity to null) })
        assertEquals(
            listOf(null to a, null to b, null to d, null to c, null to e),
            actual.filter { it != f.activity to null })
    }

    @Test
    fun `model0 movem`() {
        assertEquals(5.0, PerformanceAnalyzer(emptyLog, model0).movem)
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

    private val model4: CausalNet

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


    @Test
    fun `model1 fitness`() {
        assertDoubleEquals(1.0, PerformanceAnalyzer(log, model1).fitness)
    }

    @Ignore("This test is known to fail due to non-complete search in PerformanceAnalyzer (PA). PA considers only some prefixes, not all, it may thus overestimate.")
    @Test
    fun `model1 precision`() {
        // The paper gives 0.97 here, but both model representation and precision definition are different
        (getLogger("processm.experimental") as ch.qos.logback.classic.Logger).level = Level.TRACE
        assertDoubleEquals(0.978, PerformanceAnalyzer(log, model1).precision)
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

    @Ignore("This test is known to fail due to non-complete search in PerformanceAnalyzer (PA). PA considers only some prefixes, not all, it may thus overestimate.")
    @Test
    fun `model3 precision`() {
        // The paper gives 0.41 here, but both model representation and precision definition are different
        assertDoubleEquals(0.613, PerformanceAnalyzer(log, model3).precision)
    }

    @Test
    fun `model3 event level generalization full log`() {
        assertDoubleEquals(1.0, PerformanceAnalyzer(log, model3).eventLevelGeneralization, 0.01)
    }

    @Ignore("We don't support generalization anymore")
    @Test
    fun `model3 state level generalization full log`() {
        assertDoubleEquals(0.95, PerformanceAnalyzer(log, model3).stateLevelGeneralization, 0.01)
    }

    @Ignore("Known to fail due to model4 not fulfiling the assumption about #nodes < 100")
    @Test
    fun `model4 fitness`() {
        assertDoubleEquals(1.0, PerformanceAnalyzer(log, model4).fitness)
    }

    @Ignore("Known to fail due to model4 not fulfiling the assumption about #nodes < 100")
    @Test
    fun `model4 precision`() {
        assertDoubleEquals(1.0, PerformanceAnalyzer(log, model4).precision)
    }

    @Ignore("Known to fail due to model4 not fulfiling the assumption about #nodes < 100")
    @Test
    fun `model4 event level generalization full log`() {
        assertDoubleEquals(0.99, PerformanceAnalyzer(log, model4).eventLevelGeneralization, 0.01)
    }

    @Ignore("We don't support generalization anymore")
    @Test
    fun `model4 state level generalization full log`() {
        assertDoubleEquals(0.61, PerformanceAnalyzer(log, model4).stateLevelGeneralization, 0.01)
    }

    @Test
    fun test() {
        for (alignment in PerformanceAnalyzer(emptyLog, model3).allFreePartialAlignments(listOf(model3.start, a, b))) {
            println(alignment.state.mapToSet { it.target })
        }
    }

    @Test
    fun `model6 alignment without start and end`() {
        val alignment =
            PerformanceAnalyzer(emptyLog, model6).computeOptimalAlignment(trace(a, b, c, d, e), 100).alignment
        assertNotNull(alignment)
        assertAlignmentEquals(
            2.0,
            listOf(null to model6.start, a to a, b to b, c to c, d to d, e to e, null to model6.end),
            alignment
        )
    }

    @Test
    fun `model6 alignment ignoring start and end`() {
        val alignment =
            PerformanceAnalyzer(emptyLog, model6, SkipSilentForFree(StandardDistance())).computeOptimalAlignment(
                trace(
                    a,
                    b,
                    c,
                    d,
                    e
                ), 100
            ).alignment
        assertNotNull(alignment)
        assertAlignmentEquals(
            0.0,
            listOf(null to model6.start, a to a, b to b, c to c, d to d, e to e, null to model6.end),
            alignment
        )
    }

    @Test
    fun `model6 alignment without start and end with cost limiting`() {
        val alignment =
            PerformanceAnalyzer(emptyLog, model6).computeOptimalAlignment(trace(a, b, c, d, e), 100, 0.0)?.alignment
        assertNull(alignment)
    }

    @Test
    fun `model6 alignment ignoring start and end with cost limiting`() {
        val alignment =
            PerformanceAnalyzer(emptyLog, model6, SkipSilentForFree(StandardDistance())).computeOptimalAlignment(
                trace(a, b, c, d, e),
                100,
                0.0
            )?.alignment
        assertNotNull(alignment)
        assertAlignmentEquals(
            0.0,
            listOf(null to model6.start, a to a, b to b, c to c, d to d, e to e, null to model6.end),
            alignment
        )
    }

    @Test
    fun `nongreedy alignment`() {
        val alignment =
            PerformanceAnalyzer(emptyLog, model7).computeOptimalAlignment(trace(a, b, e, c, d, e), 100).alignment
        assertNotNull(alignment)
        println(alignment.alignment.toList().map { "${it.event?.conceptName} -> ${it.activity}" })
        assertAlignmentEquals(
            2.0,
            listOf(a to a, b to null, e to null, c to c, d to d, e to e),
            alignment
        )
    }

    @Test
    fun `deferred prize`() {
        val model = causalnet {
            start = a
            end = e
            a splits b + f or b + g or b + h
            b splits c
            c splits d
            d splits e
            f splits e
            g splits e
            h splits e
            a joins b
            b joins c
            c joins d
            d + f or d + g or d + h join e
            a joins f
            a joins g
            a joins h
        }
        val alignments = listOf(f, g, h).associateWith {
            PerformanceAnalyzer(emptyLog, model).computeOptimalAlignment(
                trace(
                    a,
                    b,
                    c,
                    d,
                    it,
                    e
                ), 100
            )
        }
        val length = alignments.values.map { it.counter }
        println(length)
        assertTrue { length.maxOrNull()!! <= 7 }
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

    @Ignore("Intended for manual execution")
    @Test
    fun `BPIC15_2f`() {
        (getLogger("processm.experimental") as ch.qos.logback.classic.Logger).level = Level.WARN
        val log = load("../xes-logs/BPIC15_2f.xes.gz")
        val offline = OnlineMiner()
        offline.processLog(log)
        println(offline.result)
        //val partialLog = Log(log.traces.toList().subList(0, 400).asSequence())
        val partialLog = Log(log.traces.toList().subList(443, 444).asSequence())
        //(getLogger("processm.experimental") as ch.qos.logback.classic.Logger).level = Level.TRACE
        val pa = PerformanceAnalyzer(partialLog, offline.result, SkipSilentForFree(StandardDistance()))
        println(pa.precision)
    }


    private fun windowIndices(n: Int, windowSize: Int, step: Int = 1, start: Int = 0) = (start until n - step step step)
        .asSequence()
        .map { windowStart ->
            val windowEnd = min(windowStart + windowSize, n)
            val previousWindowStart = if (windowStart - start >= step) windowStart - step else start
            val previousWindowEnd = if (windowStart - start >= step) windowEnd - step else start
            return@map Triple(
                IntRange(windowStart, windowEnd - 1),
                IntRange(previousWindowEnd, windowEnd - 1),
                IntRange(previousWindowStart, windowStart - 1)
            )
        }


    @Ignore("This takes way too long to be executed everytime")
    @Test
    fun `BPIC15_2f  - sweeping`() {
        val windowSize = 20
        val step = 1
        (getLogger("processm.experimental") as ch.qos.logback.classic.Logger).level = Level.WARN
        (getLogger("processm.miners") as ch.qos.logback.classic.Logger).level = Level.WARN
        val log = load("../xes-logs/BPIC15_2f.xes.gz").traces.toList()
        val hm = OnlineMiner()
        for ((windowIndices, addIndices, removeIndices) in windowIndices(
            log.size - windowSize,
            windowSize,
            start = 0
        )) {
            val remove = log.subList(removeIndices.first, removeIndices.last + 1)
            val add = log.subList(addIndices.first, addIndices.last + 1)
            val test = log.subList(windowIndices.last, windowIndices.last + windowSize)
            hm.processDiff(Log(add.asSequence()), Log(remove.asSequence()))
//            val patrain = PerformanceAnalyzer(Log(train.asSequence()), hm.result, SkipSpecialForFree(StandardDistance()))
            val patest = PerformanceAnalyzer(Log(test.asSequence()), hm.result, SkipSilentForFree(StandardDistance()))
//            println("@${windowIndices.first} train fitness=${patrain.fitness} test fitness=${patest.fitness} test prec=${patest.precision}")
            println("@${windowIndices.first} test fitness=${patest.fitness} test prec=${patest.precision}")
        }
    }

    @Test
    @Ignore("Intended for manual execution")
    fun `BPIC15_2f  - h2 admissibility`() {
        val windowSize = 20
        val start = 156
        (getLogger("processm.experimental") as ch.qos.logback.classic.Logger).level = Level.WARN
        val log = load("../xes-logs/BPIC15_2f.xes.gz").traces.toList()
        val windowIndices = IntRange(start, start + windowSize - 1)
        val train = log.subList(windowIndices.first, windowIndices.last + 1)
        val test = log.subList(windowIndices.last, windowIndices.last + windowSize)
        val hm = OnlineMiner()
        hm.processLog(Log(train.asSequence()))
        val patest =
            PerformanceAnalyzer(Log(test.asSequence()), hm.result, SkipSilentForFree(StandardDistance()))
        println("@${windowIndices.first} test fitness=${patest.fitness} test prec=${patest.precision}")
    }

    @Test
    fun `BPIC15_2f  - h1 admissibility 3`() {
        val windowSize = 20
        val start = 209
        (getLogger("processm.experimental") as ch.qos.logback.classic.Logger).level = Level.WARN
        val log = load("../xes-logs/BPIC15_2f.xes.gz").traces.toList()
        val windowIndices = IntRange(start, start + windowSize - 1)
        val train = log.subList(windowIndices.first, windowIndices.last + 1)
        val test = log.subList(windowIndices.last + windowSize - 1, windowIndices.last + windowSize)
        val hm = OnlineMiner()
        hm.processLog(Log(train.asSequence()))
        val patest =
            PerformanceAnalyzer(Log(test.asSequence()), hm.result, SkipSilentForFree(StandardDistance()))
        //patest.computeOptimalAlignment(test[0], Integer.MAX_VALUE)
        println("@${windowIndices.first} test fitness=${patest.fitness} test prec=${patest.precision}")
    }

    @Test
    fun `BPIC15_2f  - 149`() {
        val windowSize = 20
        val step = 1
        (getLogger("processm.experimental") as ch.qos.logback.classic.Logger).level = Level.WARN
        val log = load("../xes-logs/BPIC15_2f.xes.gz").traces.toList()
        val hm = OnlineMiner()
        for ((windowIndices, addIndices, removeIndices) in windowIndices(
            log.size - windowSize,
            windowSize,
            start = 0
        )) {
            val remove = log.subList(removeIndices.first, removeIndices.last + 1)
            val add = log.subList(addIndices.first, addIndices.last + 1)
            val test = log.subList(windowIndices.last, windowIndices.last + windowSize)
            hm.processDiff(Log(add.asSequence()), Log(remove.asSequence()))
//            val patrain = PerformanceAnalyzer(Log(train.asSequence()), hm.result, SkipSpecialForFree(StandardDistance()))
            if (windowIndices.first == 149) {
                val patest =
                    PerformanceAnalyzer(Log(test.asSequence()), hm.result, SkipSilentForFree(StandardDistance()))
//            println("@${windowIndices.first} train fitness=${patrain.fitness} test fitness=${patest.fitness} test prec=${patest.precision}")
                println("@${windowIndices.first} test fitness=${patest.fitness} test prec=${patest.precision}")
                break
            }
        }
    }

    /// This concerns processm.miners.cnet.onlineminer.SingleReplayer.production
    @Test
    fun `BPIC15_2f - zero in denominator`() {
        val windowSize = 20
        (getLogger("processm.experimental") as ch.qos.logback.classic.Logger).level = Level.WARN
        val log = load("../xes-logs/BPIC15_2f.xes.gz").traces.toList()
        val hm = OnlineMiner()
        for ((windowIndices, addIndices, removeIndices) in windowIndices(150, windowSize, start = 100)) {
            println("$windowIndices -> + $addIndices - $removeIndices")
            val remove = log.subList(removeIndices.first, removeIndices.last + 1)
            val add = log.subList(addIndices.first, addIndices.last + 1)
            hm.processDiff(Log(add.asSequence()), Log(remove.asSequence()))
        }
    }

    @Test
    fun `BPIC15_2f - h1 admissibility`() {
        val windowSize = 20
        (getLogger("processm.experimental") as ch.qos.logback.classic.Logger).level = Level.WARN
        val log = load("../xes-logs/BPIC15_2f.xes.gz").traces.toList()
        val hm = OnlineMiner()
        val start = 92
        val train = log.subList(start, start + windowSize)
        val test = log.subList(start + 2 * windowSize - 1, start + 2 * windowSize)
        hm.processDiff(Log(train.asSequence()), Log(emptySequence()))
        //(getLogger("processm.experimental") as ch.qos.logback.classic.Logger).level = Level.TRACE
        val patest = PerformanceAnalyzer(Log(test.asSequence()), hm.result, SkipSilentForFree(StandardDistance()))
        println("@$start test prec=${patest.precision}")
    }

    @Test
    fun `WHM - illegal diff`() {
        val windowSize = 20
        val step = 1
        (getLogger("processm.experimental") as ch.qos.logback.classic.Logger).level = Level.WARN
        val log = load("../xes-logs/BPIC15_2f.xes.gz").traces.toList()
        val hm = OnlineMiner()
        assertThrows<IllegalStateException> {
            for (start in 0 until 113) {
                val remove =
                    if (start > windowSize) log.subList(start - windowSize - step, start - windowSize) else emptyList()
                val add = log.subList(start + windowSize - step, start + windowSize)
                hm.processDiff(Log(add.asSequence()), Log(remove.asSequence()))
            }
        }
    }

    @Test
    fun `CoSeLoG_WABO_2`() {
        val log = load("../xes-logs/CoSeLoG_WABO_2.xes.gz")
        val offline = OfflineHeuristicMiner(
            bindingProvider = BestFirstBindingProvider(maxQueueSize = 100),
            longDistanceDependencyMiner = VoidLongDistanceDependencyMiner()
        )
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
        val log = load("../xes-logs/bpi_challenge_2017.xes.gz")
        val offline = OfflineHeuristicMiner(
            bindingProvider = BestFirstBindingProvider(maxQueueSize = 100),
            longDistanceDependencyMiner = VoidLongDistanceDependencyMiner()
        )
        offline.processLog(log)
        println(offline.result)
    }


    @Ignore
    @Test
    fun `nasa-cev-complete-splitted`() {
        val fulllog = load("../xes-logs/nasa-cev-complete-splitted.xes.gz")
        val log = filterLog(fulllog)
        val offline = OfflineHeuristicMiner(
            bindingProvider = BestFirstBindingProvider(maxQueueSize = 100),
            longDistanceDependencyMiner = VoidLongDistanceDependencyMiner()
        )
        offline.processLog(log)
        println(offline.result)
        val pa = PerformanceAnalyzer(log, offline.result, SkipSilentForFree(StandardDistance()))
        val bot = "⊥"
//        for((i, a) in pa.optimalAlignment.withIndex()) {
//            println("$i -> ${a.cost}")
////            for(step in a.alignment)
////                println("\t${step.event?.conceptName?:bot} -> ${step.activity?:bot}")
//        }
        println(pa.fitness)
        println(pa.precision)
    }

    @Ignore("Requires sampling")
    @Test
    fun `CoSeLoG_WABO_2 - windowing`() {
        (getLogger("processm.experimental") as ch.qos.logback.classic.Logger).level = Level.DEBUG
        val fulllog = load("../xes-logs/CoSeLoG_WABO_2.xes.gz")
        val log = filterLog(fulllog)
        val offline = OnlineMiner()
        offline.processLog(log)
        println(offline.result)
        /*
        File("../gurobi/model.py").writeText(offline.result.toPython())
        assert(false)
         */
        val dst = object : SkipSilentForFree(StandardDistance()) {
            override val maxAcceptableDistance: Double = 0.5
        }
        val partial = Log(log.traces.toList().subList(0, 1).asSequence())
        val pa = PerformanceAnalyzer(partial, offline.result, SkipSilentForFree(StandardDistance()))
        val bot = "⊥"
//        for((i, a) in pa.optimalAlignment.withIndex()) {
//            println("$i -> ${a.cost}")
////            for(step in a.alignment)
////                println("\t${step.event?.conceptName?:bot} -> ${step.activity?:bot}")
//        }
//        println(pa.fitness)
        println(pa.precision)
    }
/*
    class TranslatingEvent(base:Event, cn:String?):Event() {
        override var conceptName: String? = cn
        override fun hashCode(): Int = Objects.hash(base, cn)
        override fun equals(other: Any?): Boolean {
            return super.equals(other)
        }
    }

    class Translator(val base:Sequence<Trace>):Sequence<Trace> {

        private val translations = HashMap<String, String>()
        private var ctr = 0
        private val alphabet = "abcdefghijklmnopqrstuvwxyz"

        override fun iterator(): Iterator<Trace> = base.map {translate(it)}.iterator()

        private fun translate(inp:Trace):Trace = Trace(inp.events.map { translate(it) })
        private fun translate(inp:Event):Event = TranslatingEvent(inp, translate(inp.conceptName))
        private fun translate(inp:String?):String? = if(inp!=null)
            translations.computeIfAbsent(inp) {
            var result = ""
            var i = ctr
            ctr++
            while(i != 0) {
                result += alphabet[i%alphabet.length]
                i/=alphabet.length
            }
            return@computeIfAbsent result
        } else null

    }
 */

    @Ignore("Intended for manual execution")
    @Test
    fun `nasa-cev-complete-splitted - windowing`() {
        (getLogger("processm.experimental") as ch.qos.logback.classic.Logger).level = Level.INFO
        val log = filterLog(load("../xes-logs/nasa-cev-complete-splitted.xes.gz"))
        val offline = OnlineMiner()
        offline.processLog(log)
        // dla pełnego logu test działa niecałe 5 min, dla pierwszych 500 traces poniżej minuty
        val partial = Log(log.traces.toList().subList(0, 500).asSequence())
        val pa = PerformanceAnalyzer(partial, offline.result, SkipSilentForFree(StandardDistance()))
        val precision = pa.precision
        assertTrue(0.39 <= precision)
        assertTrue(precision <= 0.40)
    }

    @Ignore
    @Test
    fun `blah`() {
        val a1 = Node("a1")
        val a2 = Node("a2")
        val a3 = Node("a3")
        val b = Node("b")
        val c1 = Node("c1")
        val c2 = Node("c2")
        val c3 = Node("c3")
        val model = causalnet {
            start splits a1 + a2 + a3
            a1 splits b
            a2 splits b
            a3 splits b
            b splits c1 or c2 or c3
            c1 splits end
            c2 splits end
            c3 splits end
            start joins a1
            start joins a2
            start joins a3
            a1 or a2 or a3 join b
            b joins c1
            b joins c2
            b joins c3
            c1 + c2 + c3 join end
        }
//        println(model.toDanielJS("test"))
//        CausalNetVerifierImpl(model).validSequences.forEach { println(it.map { it.a }) }
    }

    fun logFromModel(model: CausalNet): Log {
        val tmp = CausalNetVerifier().verify(model).validLoopFreeSequences.map { seq -> seq.map { it.a } }
            .toSet()
        return Log(tmp.map { seq -> Trace(seq.asSequence().map { event(it.name) }) }.asSequence())
    }

    fun logFromString(text: String): Log =
        Log(
            text.split('\n')
                .map { line -> Trace(line.split(" ").filter { it.isNotEmpty() }.map { event(it) }.asSequence()) }
                .asSequence()
        )

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
        val pa = PerformanceAnalyzer(log, dodReference)
        assertDoubleEquals(1.0, pa.precision)
        assertDoubleEquals(1.0, pa.perfectFitRatio)
    }

    @Test
    fun `loops`() {
        val log = logFromString(
            """
            a b  e
            a b c  e
            a b c d e
            a b c d b  e
            a b c d b c  e
            a b c d b c d  e
        """.trimIndent()
        )
        val hm = OnlineMiner()
        hm.processDiff(log, Log(emptySequence()))
        println(hm.result)
        val str = "a " + (0..100).joinToString(separator = " ") { "b c d" } + " e"
        println(str)
        val log2 = logFromString(str)
        //      (getLogger("processm.experimental") as ch.qos.logback.classic.Logger).level = Level.TRACE
        println(PerformanceAnalyzer(log2, hm.result, SkipSilentForFree(StandardDistance())).precision)
    }

    private fun testPossible(model: CausalNet, maxSeqLen: Int = Int.MAX_VALUE, maxPrefixLen: Int = Int.MAX_VALUE) {
        val validSequences = CausalNetVerifierImpl(model)
            .computeSetOfValidSequences(false) { it, _ -> it.size < maxSeqLen }
            .map { it.map { it.a } }.toList()
        val prefix2possible = HashMapWithDefault<List<Node>, HashSet<Activity>>() { HashSet() }
        for (seq in validSequences) {
            for (i in 0 until min(seq.size, maxPrefixLen))
                prefix2possible[seq.subList(0, i)].add(seq[i])
        }
        println(validSequences)
        println(prefix2possible)
        val pa = PerformanceAnalyzer(Log(emptySequence()), model, SkipSilentForFree(StandardDistance()))
        for ((prefix, expected) in prefix2possible.entries) {
            val actual = pa.possibleNext(listOf(prefix)).values.single()
            assertEquals(expected, actual, "prefix=$prefix expected=$expected actual=$actual")
        }
    }

    @Ignore(
        """This test is known to fail due to oversimplification in PerformanceAnalyzer (PA). 
|PA doesn't verify if there really exists a valid sequence, only the existence of a prefix, which may be impossible
|to complete into a valid sequence."""
    )
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

    @Ignore("This test is known to fail due to non-complete search in PerformanceAnalyzer (PA). PA considers only some prefixes, not all.")
    @Test
    fun possible2() {
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
        println(model)
        val s = model.start
        val pa = PerformanceAnalyzer(Log(emptySequence()), model, SkipSilentForFree(StandardDistance()))
        assertEquals(setOf(a), pa.possibleNext(listOf(listOf(s))).values.single())
        assertEquals(setOf(a, c), pa.possibleNext(listOf(listOf(s, a))).values.single())
        assertEquals(setOf(a, c), pa.possibleNext(listOf(listOf(s, a, a))).values.single())
        assertEquals(setOf(b), pa.possibleNext(listOf(listOf(s, a, a, c))).values.single())
        assertEquals(setOf(b), pa.possibleNext(listOf(listOf(s, a, a, c, b))).values.single())
        assertEquals(setOf(model.end), pa.possibleNext(listOf(listOf(s, a, a, c, b, b))).values.single())
    }

    @Test
    fun possible3() {
        val a = List(3) { i -> Node("a$i") }
        val b = List(3) { i -> Node("b$i") }
        val c = List(3) { i -> Node("c$i") }
        val model = causalnet {
            start splits a[0] or a[1] or a[2] or a[0] + a[1] or a[0] + a[2] or a[1] + a[2] or a[0] + a[1] + a[2]
            a[0] splits b[0] or b[1] or b[2] or b[0] + b[1] or b[0] + b[2] or b[1] + b[2] or b[0] + b[1] + b[2]
            a[1] splits b[0] or b[1] or b[2] or b[0] + b[1] or b[0] + b[2] or b[1] + b[2] or b[0] + b[1] + b[2]
            a[2] splits b[0] or b[1] or b[2] or b[0] + b[1] or b[0] + b[2] or b[1] + b[2] or b[0] + b[1] + b[2]
            b[0] splits c[0] or c[1] or c[2] or c[0] + c[1] or c[0] + c[2] or c[1] + c[2] or c[0] + c[1] + c[2]
            b[1] splits c[0] or c[1] or c[2] or c[0] + c[1] or c[0] + c[2] or c[1] + c[2] or c[0] + c[1] + c[2]
            b[2] splits c[0] or c[1] or c[2] or c[0] + c[1] or c[0] + c[2] or c[1] + c[2] or c[0] + c[1] + c[2]
            c[0] splits end
            c[1] splits end
            c[2] splits end
            start joins a[0]
            start joins a[1]
            start joins a[2]
            a[0] or a[1] or a[2] or a[0] + a[1] or a[0] + a[2] or a[1] + a[2] or a[0] + a[1] + a[2] join b[0]
            a[0] or a[1] or a[2] or a[0] + a[1] or a[0] + a[2] or a[1] + a[2] or a[0] + a[1] + a[2] join b[1]
            a[0] or a[1] or a[2] or a[0] + a[1] or a[0] + a[2] or a[1] + a[2] or a[0] + a[1] + a[2] join b[2]
            b[0] or b[1] or b[2] or b[0] + b[1] or b[0] + b[2] or b[1] + b[2] or b[0] + b[1] + b[2] join c[0]
            b[0] or b[1] or b[2] or b[0] + b[1] or b[0] + b[2] or b[1] + b[2] or b[0] + b[1] + b[2] join c[1]
            b[0] or b[1] or b[2] or b[0] + b[1] or b[0] + b[2] or b[1] + b[2] or b[0] + b[1] + b[2] join c[2]
            c[0] or c[1] or c[2] or c[0] + c[1] or c[0] + c[2] or c[1] + c[2] or c[0] + c[1] + c[2] join end
        }
        val traces = sequenceOf<Trace>(
            trace(a[0], a[1], b[0], c[0], b[1], c[1])
        )
        val pa = PerformanceAnalyzer(Log(traces), model, SkipSilentForFree(StandardDistance()))
        println(pa.precision)
        //pa.possibleNext(listOf(listOf(model.start, a[0], a[1])))
        //pa.replayWithSearch(listOf(model.start, a[0], a[1], c[0]))
    }

}
