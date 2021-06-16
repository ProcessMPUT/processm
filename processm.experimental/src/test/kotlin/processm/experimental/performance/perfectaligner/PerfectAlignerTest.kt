package processm.experimental.performance.perfectaligner

import org.junit.jupiter.api.Disabled
import processm.core.log.Event
import processm.core.log.XMLXESInputStream
import processm.core.log.hierarchical.HoneyBadgerHierarchicalXESInputStream
import processm.core.log.hierarchical.InMemoryXESProcessing
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.causalnet
import processm.experimental.onlinehmpaper.createDriftLogs
import processm.experimental.onlinehmpaper.filterLog
import processm.miners.causalnet.onlineminer.OnlineMiner
import java.io.File
import java.io.FileInputStream
import java.util.zip.GZIPInputStream
import kotlin.test.*

@InMemoryXESProcessing
class PerfectAlignerTest {

    private val a = Node("a")
    private val b = Node("b")
    private val c = Node("c")
    private val d = Node("d")
    private val e = Node("e")
    private val f = Node("f")
    private val g = Node("g")
    private val h = Node("h")

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


    @Test
    fun `model0 trace1`() {
        val fa = PerfectAligner(IntCausalNet(model0))
        val trace = fa.traceToInt(trace(a, b, c, d, e))
        val alignment = fa.align(trace)
        assertNotNull(alignment)
        System.out.println(alignment)
    }

    @Test
    fun `model0 trace2`() {
        val fa = PerfectAligner(IntCausalNet(model0))
        val trace = fa.traceToInt(trace(a, b, d, c, e))
        val alignment = fa.align(trace)
        assertNotNull(alignment)
        System.out.println(alignment)
    }

    @Test
    fun `model0 incomplete trace 1`() {
        val fa = PerfectAligner(IntCausalNet(model0))
        val trace = fa.traceToInt(trace(a, b, c, e))
        assertNull(fa.align(trace))
    }

    @Disabled("Intended for manual execution")
    @Test
    fun `BPIC15_2-subset`() {
        val log = FileInputStream("src/test/resources/BPIC15_2-subset.xes").use { base ->
            HoneyBadgerHierarchicalXESInputStream(XMLXESInputStream(base)).first()
        }
        val online = OnlineMiner()
        for(trace in log.traces) {
            val addLog = Log(sequenceOf(trace))
            val removeLog = Log(emptySequence())
            online.processDiff(addLog, removeLog)
        }
        val fa= PerfectAligner(online.result)
        for(trace in log.traces)
            assertNotNull(fa.align(trace))
    }

    @Disabled("Intended for manual execution")
    @Test
    fun `BPIC15_2`() {
        val log = FileInputStream("src/test/resources/BPIC15_2-subset.xes").use { base ->
            HoneyBadgerHierarchicalXESInputStream(XMLXESInputStream(base)).first()
        }
        val completeLog = FileInputStream("../xes-logs/BPIC15_2.xes.gz").use { base ->
            HoneyBadgerHierarchicalXESInputStream(XMLXESInputStream(GZIPInputStream(base))).first()
        }
        val online = OnlineMiner()
        for(trace in log.traces) {
            val addLog = Log(sequenceOf(trace))
            val removeLog = Log(emptySequence())
            online.processDiff(addLog, removeLog)
        }
        val fa= PerfectAligner(online.result)
        var notNullCounter = 0
        for(trace in completeLog.traces) {
            val alignment =fa.align(trace, 100*trace.events.count())
            println(alignment)
            if(alignment!=null)
                notNullCounter++
        }
        println("notNullCounter=$notNullCounter log size=${completeLog.traces.count()}")
        assertTrue { notNullCounter >= log.traces.count() }
        assertEquals(32, notNullCounter)
    }

    @Test
    fun `BPIC15_2 drift at 28`() {
        val logfile = File("../xes-logs/BPIC15_2.xes.gz")
        val splitSeed = 3737844653L
        val sampleSeed = 12648430L
        val keval = 5
        val knownNamesThreshold = 100
        val missThreshold = 10
        val windowSize = 25
        val completeLog = logfile.inputStream().use { base ->
            filterLog(HoneyBadgerHierarchicalXESInputStream(XMLXESInputStream(GZIPInputStream(base))).first())
        }
        val logs = createDriftLogs(
            completeLog,
            sampleSeed,
            splitSeed,
            keval,
            knownNamesThreshold,
            missThreshold
        )
        val flatLog = logs.flatten()
        val hm= OnlineMiner()
        val windowEnd = 28
        val windowStart = windowEnd - windowSize + 1
        val trainLog = flatLog.subList(windowStart, windowEnd+1)
        hm.processDiff(Log(trainLog.asSequence()), Log(emptySequence()))
        val pa=PerfectAligner(hm.result)
        println(trainLog.maxOf { it.events.count() }*100)
        val pfrApprox = pa.perfectFitRatio(Log(trainLog.asSequence()), 100)
        assertEquals(0.96, pfrApprox)
        val pfr = pa.perfectFitRatio(Log(trainLog.asSequence()))
        assertEquals(1.0, pfr)
    }

    @Test
    fun `BPIC15_2 drift at 177`() {
        val logfile = File("../xes-logs/BPIC15_2.xes.gz")
        val completeLog = logfile.inputStream().use { base ->
            filterLog(HoneyBadgerHierarchicalXESInputStream(XMLXESInputStream(GZIPInputStream(base))).first())
        }

        val trainLog = Log(sequenceOf(completeLog.traces.toList()[520]))
        val hm= OnlineMiner()
        hm.processDiff(trainLog, Log(emptySequence()))
        val pa=PerfectAligner(hm.result)
        val pfr = pa.perfectFitRatio(trainLog)
        assertEquals(1.0, pfr)
    }
}