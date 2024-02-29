package processm.miners.causalnet.onlineminer

import ch.qos.logback.classic.Level
import processm.core.log.Helpers.logFromModel
import processm.core.log.Helpers.logFromString
import processm.core.log.hierarchical.Log
import processm.core.models.causalnet.*
import processm.core.models.metadata.BasicMetadata
import processm.core.models.metadata.SingleDoubleMetadata
import processm.logging.logger
import processm.miners.causalnet.onlineminer.replayer.SingleReplayer
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OnlineMinerTest {

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

    private fun test(log: Log, model: CausalNet) {
        val replayer = BasicReplayer(model)
        val usedBindings = HashSet<Binding>()
        for (trace in log.traces) {
            val tmp = listOf(model.start) + BasicTraceToNodeTrace()(trace) + listOf(model.end)
            val replay = replayer.replay(tmp)
                .map { it.toList() }.toList()
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
    fun `loops are hard`() {
        val log = logFromString(
            """
            a b c d e
            a b b c d d e
            a b b b c d d d e
        """.trimIndent()
        )
        val hm = OnlineMiner()
        (SingleReplayer.logger() as ch.qos.logback.classic.Logger).level = Level.TRACE
        hm.processDiff(log, Log(emptySequence()))
        test(log, hm.result)
    }

    @Test
    @Ignore("Takes a bit too long, because replayer used for testing is slow")
    fun `diamond of diamonds - batch`() {
        val log = logFromModel(dodReference)
        val hm = OnlineMiner(SingleReplayer())
        (hm.logger() as ch.qos.logback.classic.Logger).level = Level.TRACE
        hm.processDiff(log, Log(emptySequence()))
        test(log, hm.result)
    }

    data class Window(val add: IntRange, val remove: IntRange, val window: IntRange)

    private fun windowGenerator(windowSize: Int, stepSize: Int, n: Int) = sequence {
        for (i in stepSize..(n + n % stepSize) step stepSize) {
            val add = IntRange(i - stepSize, min(i, n) - 1)
            val remove = IntRange(max(i - stepSize - windowSize, 0), max(min(i, n) - windowSize, 0) - 1)
            val window = IntRange(remove.last + 1, add.last)
            yield(Window(add, remove, window))
        }
    }

    @Test
    fun `diamond of diamonds - window - short`() {
        val windowSize = 20
        val stepSize = 1
        val log = logFromModel(dodReference)
        val traces = log.traces.toList()
        val hm = OnlineMiner()
        for (step in windowGenerator(windowSize, stepSize, 50)) {
            val add = if (!step.add.isEmpty()) traces.subList(step.add.first, step.add.last + 1) else emptyList()
            val remove =
                if (!step.remove.isEmpty()) traces.subList(step.remove.first, step.remove.last + 1) else emptyList()
            hm.processDiff(Log(add.asSequence()), Log(remove.asSequence()))
            val window = traces.subList(step.window.first, step.window.last + 1)
            test(Log(window.asSequence()), hm.result)
        }
    }

    @Test
    @Ignore("Takes a bit too long, because replayer used for testing is slow")
    fun `diamond of diamonds - window`() {
        val windowSize = 20
        val stepSize = 1
        val log = logFromModel(dodReference)
        val traces = log.traces.toList()
        val hm = OnlineMiner()
        for (step in windowGenerator(windowSize, stepSize, traces.size)) {
            val add = if (!step.add.isEmpty()) traces.subList(step.add.first, step.add.last + 1) else emptyList()
            val remove =
                if (!step.remove.isEmpty()) traces.subList(step.remove.first, step.remove.last + 1) else emptyList()
            hm.processDiff(Log(add.asSequence()), Log(remove.asSequence()))
            val window = traces.subList(step.window.first, step.window.last + 1)
            test(Log(window.asSequence()), hm.result)
        }
    }

    @Test
    fun test() {
        val log = logFromString(
            """
            a b d
            a c d
            a b c d            
        """.trimIndent()
        )
        val hm = OnlineMiner()
        hm.processLog(log)
        test(log, hm.result)
        val b = Node("b")
        val c = Node("c")
        val d = Node("d")
        assertTrue { BasicMetadata.DEPENDENCY_MEASURE in hm.result.availableMetadata }
        assertEquals(
            2.0,
            (hm.result.getMetadata(Dependency(a, b), BasicMetadata.DEPENDENCY_MEASURE) as SingleDoubleMetadata)
                .value
        )
        assertEquals(
            1.0,
            (hm.result.getMetadata(Dependency(a, c), BasicMetadata.DEPENDENCY_MEASURE) as SingleDoubleMetadata)
                .value
        )
        assertEquals(
            1.0,
            (hm.result.getMetadata(Dependency(b, c), BasicMetadata.DEPENDENCY_MEASURE) as SingleDoubleMetadata)
                .value
        )
        assertEquals(
            1.0,
            (hm.result.getMetadata(Dependency(b, d), BasicMetadata.DEPENDENCY_MEASURE) as SingleDoubleMetadata)
                .value
        )
        assertEquals(
            2.0,
            (hm.result.getMetadata(Dependency(c, d), BasicMetadata.DEPENDENCY_MEASURE) as SingleDoubleMetadata)
                .value
        )
    }

}
