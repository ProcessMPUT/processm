package processm.miners.heuristicminer.windowing

import ch.qos.logback.classic.Level
import com.sun.xml.bind.v2.runtime.unmarshaller.XsiNilLoader
import processm.core.log.XMLXESInputStream
import processm.core.log.hierarchical.HoneyBadgerHierarchicalXESInputStream
import processm.core.log.hierarchical.InMemoryXESProcessing
import processm.core.log.hierarchical.Log
import processm.core.logging.logger
import processm.core.models.causalnet.*
import processm.miners.heuristicminer.BasicTraceToNodeTrace
import processm.miners.heuristicminer.Helper
import processm.miners.heuristicminer.Helper.logFromModel
import processm.miners.heuristicminer.Helper.logFromString
import processm.miners.heuristicminer.NodeTrace
import processm.miners.heuristicminer.windowing.Replayer
import processm.miners.heuristicminer.windowing.SingleReplayer
import processm.miners.heuristicminer.windowing.WindowingHeuristicMiner
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertTrue

class WindowingHeuristicMinerTest {

//    @BeforeTest
//    fun beforek() {
//        (hm.logger() as ch.qos.logback.classic.Logger).level = Level.WARN
//    }

    @Test
    fun diamond() {
        val log = Helper.logFromString(
            """
                a b c d
                a c b d 
            """.trimIndent()
        )
        val hm = WindowingHeuristicMiner()
        (SingleReplayer.logger() as ch.qos.logback.classic.Logger).level = Level.TRACE
        hm.processLog(log)
        println(hm.result)
    }

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

    private fun test(log:Log, model:CausalNet) {
        val replayer = BasicReplayer(model)
        val usedBindings = HashSet<Binding>()
        for (trace in log.traces) {
            val tmp = listOf(model.start) + BasicTraceToNodeTrace()(trace) + listOf(model.end)
            val replay = replayer.replay(tmp)
                .map { it.toList() }.toList()
            println("${replay.size} ${trace.events.map{it.conceptName}.toList()}")
            assertTrue { replay.size == replay.toSet().size }
            assertTrue { replay.isNotEmpty() }
            assertTrue { replay.all { it.isNotEmpty() } }
            replay.flatMapTo(usedBindings) { replay -> replay.mapNotNull { it.binding } }
        }
        val unusedJoins = model.joins.values.flatten() - usedBindings
        val unusedSplits = model.splits.values.flatten() - usedBindings
        assertTrue("Unused joins: $unusedJoins") { unusedJoins.isEmpty() }
        assertTrue("Unused splits: $unusedSplits") { unusedSplits.isEmpty() }
    }

    @Test
    fun `diamond of diamonds - incremental`() {
        val log = logFromModel(dodReference)
        val hm = WindowingHeuristicMiner()
        for (trace in log.traces)
            hm.processDiff(Log(sequenceOf(trace)), Log(emptySequence()))
        test(log, hm.result)
    }

    @Test
    fun `loops prove that a run-all-consume-all strategy is not enough`() {
        val log = logFromString("""
            a b c
            a b b c
            a b b b c
        """.trimIndent())
        val hm = WindowingHeuristicMiner()
        (SingleReplayer.logger() as ch.qos.logback.classic.Logger).level = Level.TRACE
        hm.processDiff(log, Log(emptySequence()))
        println(hm.result)
    }

    /*
    @Test
    fun `loops are hard`() {
        val log = logFromString("""
            a b c d e
            a b b c d d e
            a b b b c d d d e
        """.trimIndent())
        val hm = WindowingHeuristicMiner()
        (SingleReplayer.logger() as ch.qos.logback.classic.Logger).level = Level.TRACE
        hm.processDiff(log, Log(emptySequence()))
        println(hm.result)
    }
     */

    @Test
    fun `diamond of diamonds - batch`() {
        val log = logFromModel(dodReference)
        val hm = WindowingHeuristicMiner(SingleReplayer(2))
        (hm.logger() as ch.qos.logback.classic.Logger).level = Level.TRACE
        hm.processDiff(log, Log(emptySequence()))
//        test(log, hm.result)
        println(hm.result)
    }

    data class Window(val add:IntRange, val remove:IntRange, val window:IntRange)

    private fun windowGenerator(windowSize:Int, stepSize:Int, n:Int) = sequence {
        for(i in stepSize .. (n + n%stepSize) step stepSize) {
            val add = IntRange(i-stepSize , min(i, n)-1)
            val remove = IntRange(max(i-stepSize-windowSize, 0), max(min(i, n)-windowSize, 0)-1)
            val window = IntRange(remove.last +1, add.last)
            yield(
                Window(
                    add,
                    remove,
                    window
                )
            )
        }
    }

    @Test
    fun `diamond of diamonds - window`() {
        val windowSize = 20
        val stepSize = 1
        val log = logFromModel(dodReference)
        val traces = log.traces.toList()
        val hm = WindowingHeuristicMiner()
        for(step in windowGenerator(windowSize, stepSize, traces.size)) {
            val add = if(!step.add.isEmpty()) traces.subList(step.add.first, step.add.last+1) else emptyList()
            val remove = if(!step.remove.isEmpty()) traces.subList(step.remove.first, step.remove.last+1) else emptyList()
            hm.processDiff(Log(add.asSequence()), Log(remove.asSequence()))
            println(hm.result)
            val window = traces.subList(step.window.first, step.window.last+1)
            test(Log(window.asSequence()), hm.result)
        }
    }

    @InMemoryXESProcessing
    @Test
    fun `sepsis`() {
        val dummyReplayer = object : Replayer {
            override fun replayGroup(model: CausalNet, traces: List<NodeTrace>): Pair<Set<Split>, Set<Join>> = emptySet<Split>() to emptySet<Join>()

        }
        val log = File("../xes-logs/Sepsis_Cases-Event_Log.xes.gz").inputStream().use {
            HoneyBadgerHierarchicalXESInputStream(XMLXESInputStream(GZIPInputStream(it))).first()
        }
        val hm = WindowingHeuristicMiner(dummyReplayer)
        //(hm.logger() as ch.qos.logback.classic.Logger).level = Level.TRACE
//            (SingleReplayer.logger() as ch.qos.logback.classic.Logger).level = Level.TRACE
        hm.processDiff(log, Log(emptySequence()))
        println(hm.result)
        val replayer = SingleReplayer()
        val traces = log.traces
            .map { listOf(hm.result.start) + hm.traceToNodeTrace(it) + listOf(hm.result.end) }
            .toList()
        replayer.replay(hm.result, traces[0])
    }

    @InMemoryXESProcessing
    @Test
    fun `real logs`() {
//        val files = listOf("../xes-logs/BPIC15_2f.xes.gz", "../xes-logs/BPIC15_4f.xes.gz", "../xes-logs/Sepsis_Cases-Event_Log.xes.gz")
//        val files = listOf("../xes-logs/Receipt_phase_of_an_environmental_permit_application_process_WABO_CoSeLoG_project.xes.gz",
//            "../xes-logs/nasa-cev-complete-splitted.xes.gz"
//        )
        val files = listOf("../xes-logs/Sepsis_Cases-Event_Log.xes.gz")
        for(file in files) {
            println(file)
            val log = File(file).inputStream().use {
                HoneyBadgerHierarchicalXESInputStream(XMLXESInputStream(GZIPInputStream(it))).first()
            }
            val hm = WindowingHeuristicMiner()
            //(hm.logger() as ch.qos.logback.classic.Logger).level = Level.TRACE
//            (SingleReplayer.logger() as ch.qos.logback.classic.Logger).level = Level.TRACE
            hm.processDiff(log, Log(emptySequence()))
            println(hm.result)
        }
    }

    @Test
    fun test() {
        val log = logFromString("m w o m w o w m o")
        (SingleReplayer.logger() as ch.qos.logback.classic.Logger).level = Level.TRACE
        val hm=WindowingHeuristicMiner()
        hm.processLog(log)
        println(hm.result)
    }

    @Test
    fun test2() {
        val log = logFromString("a b b a c a c")
        (SingleReplayer.logger() as ch.qos.logback.classic.Logger).level = Level.TRACE
        val hm=WindowingHeuristicMiner()
        hm.processLog(log)
        println(hm.result)
    }

    @Test
    fun test3() {
        val log=logFromString("Leucocytes CRP ERRegistration ERTriage ERSepsisTriage IVLiquid IVAntibiotics CRP Leucocytes LacticAcid AdmissionNC Leucocytes LacticAcid AdmissionNC Leucocytes CRP AdmissionIC LacticAcid Leucocytes CRP ReleaseB")
        (SingleReplayer.logger() as ch.qos.logback.classic.Logger).level = Level.TRACE
        val hm=WindowingHeuristicMiner()
        hm.processLog(log)
        println(hm.result)
    }
}