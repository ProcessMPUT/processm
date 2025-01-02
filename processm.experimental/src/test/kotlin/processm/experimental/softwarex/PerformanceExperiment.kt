package processm.experimental.softwarex

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.slf4j.LoggerFactory
import processm.core.log.hierarchical.Trace
import processm.core.log.XMLXESInputStream
import processm.core.log.hierarchical.HoneyBadgerHierarchicalXESInputStream
import processm.core.log.hierarchical.InMemoryXESProcessing
import processm.core.log.hierarchical.Log
import processm.helpers.asList
import processm.miners.causalnet.onlineminer.OnlineMiner
import processm.miners.causalnet.onlineminer.replayer.SingleReplayer
import processm.miners.processtree.inductiveminer.OnlineInductiveMiner
import kotlinx.serialization.json.*
import processm.core.models.commons.ProcessModel
import processm.enhancement.kpi.Calculator
import processm.enhancement.kpi.Report
import processm.logging.logger
import processm.miners.ALGORITHM_HEURISTIC_MINER
import processm.miners.ALGORITHM_INDUCTIVE_MINER
import processm.miners.ALGORITHM_ORIGINAL_HEURISTIC_MINER
import processm.miners.causalnet.heuristicminer.OriginalHeuristicMiner

import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import java.lang.management.ManagementFactory
import java.time.Instant
import java.util.zip.GZIPInputStream
import kotlin.test.*

val JsonSerializer = Json {
    allowSpecialFloatingPointValues = true
    encodeDefaults = true
    explicitNulls = false
    prettyPrint = false
}

@OptIn(InMemoryXESProcessing::class)
class PerformanceExperiment {

    companion object {
        val STEP = 50
        val LOG_FILES = """
            CoSeLoG_WABO_2
            BPIC15_2f
            CoSeLoG_WABO_4
            BPIC15_2
            BPIC15_4f
            CoSeLoG_WABO_5
            BPIC15_1f
            CoSeLoG_WABO_1
            BPIC15_5f
            Sepsis_Cases-Event_Log
            BPIC15_4
            CoSeLoG_WABO_3
            Hospital_log
            BPIC15_5
            BPIC15_1
            BPIC15_3f
            BPIC15_3
            nasa-cev-complete-splitted
        """.trimIndent().split("\n").filterNot { it.isBlank() }.map { "../xes-logs/$it.xes.gz" }
            .also { files -> check(files.all { File(it).exists() }) }
        val MINERS = listOf(
            buildJsonObject {
                put("name", ALGORITHM_HEURISTIC_MINER)
                put("horizon", 3)
            } to { OnlineMiner(SingleReplayer(horizon = 3)) },
            buildJsonObject { put("name", ALGORITHM_INDUCTIVE_MINER) } to { OnlineInductiveMiner() },
            buildJsonObject {
                put("name", ALGORITHM_ORIGINAL_HEURISTIC_MINER)
                put("dependencyThreshold", 0.5)
                put("l1Threshold", 0.5)
                put("l2Threshold", 0.5)
                put("andThreshold", 0.65)
            } to {
                OriginalHeuristicMiner(
                    dependencyThreshold = 0.5,
                    l1Threshold = 0.5,
                    l2Threshold = 0.5,
                    andThreshold = 0.65
                )
            }
        )
        val MAX_INETRNAL_REPETITIONS = 1_000
        val INTERNAL_REPETITIONS_MULTIPLIER = 10
        val EXTERNAL_REPETITIONS = 10
        val MIN_TIME_MS = 1_000
        val COMPUTE_ALIGNMENTS = false
        val WARMUP = 10

        private lateinit var oldLogLevel: Level

        @JvmStatic
        @BeforeAll
        fun disableLogging(): Unit {
            val logger = LoggerFactory.getLogger("processm") as Logger
            oldLogLevel = logger.level
            logger.level = Level.ERROR
        }

        @JvmStatic
        @AfterAll
        fun enableLogging(): Unit {
            val logger = LoggerFactory.getLogger("processm") as Logger
            logger.level = oldLogLevel
        }
    }


    @Serializable
    private data class ResourceStats(val cpuTimeMillis: Long, val peakMemory: Long)

    private fun measureResources(block: () -> Unit): ResourceStats {
        for (pool in ManagementFactory.getMemoryPoolMXBeans())
            pool.resetPeakUsage()
        val osBean = ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean
        val start = osBean.processCpuTime
        check(start > 0) { "CPU time is not available" }
        block()
        val end = osBean.processCpuTime
        check(end >= start) { "CPU time made a roll, start=$start end=$end" }
        val nanoTime = end - start
        var mem: Long = 0
        for (pool in ManagementFactory.getMemoryPoolMXBeans()) {
            val poolMax = pool.peakUsage.used
            if (poolMax > 0)
                mem += poolMax
        }
        return ResourceStats(nanoTime / 1000000, mem)
    }


    private fun readHB(file: File): HoneyBadgerHierarchicalXESInputStream =
        file.inputStream().use { input ->
            GZIPInputStream(input).use { gzip ->
                HoneyBadgerHierarchicalXESInputStream(XMLXESInputStream(gzip)).also { it.first() /* prevent lazy read after stream is closed */ }
            }
        }

    private fun <T> measure(repeats: Int, block: () -> T?): Pair<ResourceStats, T?> {
        try {
            System.gc()
            var result: T? = null
            assert(repeats >= 1)
            val resources = measureResources {
                repeat(repeats) {
                    System.gc()
                    result = block()
                }
            }
            return resources to result
        } finally {
            System.gc()
        }
    }

    private fun sublogs(traces: List<Trace>, step: Int): Sequence<Log> = sequence {
        for (size in step..traces.size step step)
            yield(Log(traces.subList(0, size).asSequence()))
        if (traces.size % step != 0)
            yield(Log(traces.asSequence()))
    }

    @Serializable
    private data class LogStats(val nTraces: Int, val nUniqueTraces: Int, val nEvents: Int, val nUniqueEvents: Int)

    private fun computeLogStats(log: Log): LogStats {
        val nTraces = log.traces.count()
        val nUniqueTraces = log.traces.distinctBy { trace -> trace.events.map { it.conceptName }.toList() }.count()
        val nEvents = log.traces.sumOf { it.events.count() }
        val nUniqueEvents = log.traces.flatMap { it.events }.distinctBy { it.conceptName }.count()
        return LogStats(nTraces, nUniqueTraces, nEvents, nUniqueEvents)
    }

    private fun <T> autoscale(block: () -> T?): Pair<Int, Pair<ResourceStats, T?>> {
        var reps: Int = 1
        while (true) {
            val result = measure(reps, block)
            if (result.second === null || result.first.cpuTimeMillis >= MIN_TIME_MS || reps * INTERNAL_REPETITIONS_MULTIPLIER > MAX_INETRNAL_REPETITIONS)
                return reps to result
            reps *= INTERNAL_REPETITIONS_MULTIPLIER
        }
    }

    @Test
    fun test() {
        val resultStream = PrintStream(FileOutputStream("softwarex.jsonl", true))
        val experimentTimestamp = Instant.now().toString()
        run {
            // warmup
            val sublog = sublogs(readHB(File(LOG_FILES.first())).first().traces.asList(), STEP).first()
            for ((desc, minerFactory) in MINERS) {
                repeat(WARMUP) {
                    println("Warmup: $desc (${it + 1}/$WARMUP)")
                    val model = minerFactory().processLog(sequenceOf(sublog))
                    if (COMPUTE_ALIGNMENTS)
                        Calculator(model).calculate(sequenceOf(sublog))
                }
            }
        }
        for (file in LOG_FILES) {
            val stream = readHB(File(file))
            assertEquals(1, stream.count())
            val traces =
                stream.first().traces.asList()
                    .sortedBy { trace -> trace.events.minOf { it.timeTimestamp ?: Instant.MIN } }
            val logDescription = buildJsonObject {
                put("file", file)
                put("stats", JsonSerializer.encodeToJsonElement(computeLogStats(Log(traces.asSequence()))))
            }
            for ((minerDescription, minerFactory) in MINERS) {
                for (sublog in sublogs(traces, STEP)) {
                    val results = ArrayList<JsonObject>()
                    var minerReps = 0
                    var alignerReps = 0
                    repeat(EXTERNAL_REPETITIONS) {
                        val result = HashMap<String, JsonElement>()
                        val miningResult:
                                Pair<ResourceStats, ProcessModel?>
                        val block = {
                            try {
                                minerFactory().processLog(sequenceOf(sublog))
                            } catch (t: Throwable) {
                                logger().error("Miner error", t)
                                null
                            }
                        }
                        if (minerReps <= 0)
                            autoscale(block).let {
                                minerReps = it.first
                                miningResult = it.second
                            }
                        else
                            miningResult = measure(minerReps, block)
                        result["miner"] = JsonSerializer.encodeToJsonElement(miningResult.first)
                        val model = miningResult.second
                        if (COMPUTE_ALIGNMENTS && model !== null) {
                            val alignerResult: ResourceStats
                            val block = {
                                try {
                                    Calculator(model).calculate(sequenceOf(sublog))
                                } catch (t: Throwable) {
                                    logger().error("Aligner error", t)
                                    null
                                }
                            }
                            if (alignerReps <= 0)
                                autoscale(block).let {
                                    alignerReps = it.first
                                    alignerResult = it.second.first
                                }
                            else
                                alignerResult = measure(alignerReps, block).first
                            result["aligner"] = JsonSerializer.encodeToJsonElement(alignerResult)
                        }
                        results.add(JsonObject(result))
                    }
                    val entry = buildJsonObject {
                        put("timestamp", experimentTimestamp)
                        put("log", logDescription)
                        put("miner", minerDescription)
                        put("sublog", JsonSerializer.encodeToJsonElement(computeLogStats(sublog)))
                        put("minerRepetitions", minerReps)
                        put("alignerRepetitions", alignerReps)
                        put("measurements", JsonArray(results))
                    }
                    println(JsonSerializer.encodeToString(entry))
                    resultStream.println(JsonSerializer.encodeToString(entry))
                    resultStream.flush()
                }
            }
        }
    }

}