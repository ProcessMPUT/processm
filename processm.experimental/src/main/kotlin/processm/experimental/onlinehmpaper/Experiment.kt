package processm.experimental.onlinehmpaper

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import processm.core.log.Event
import processm.core.log.XMLXESInputStream
import processm.core.log.hierarchical.HoneyBadgerHierarchicalXESInputStream
import processm.core.log.hierarchical.InMemoryXESProcessing
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.logging.logger
import processm.core.models.causalnet.CausalNet
import processm.core.models.processtree.execution.ModelAnalyzer
import processm.core.models.processtree.processTree
import processm.experimental.performance.PerformanceAnalyzer
import processm.experimental.performance.SkipSpecialForFree
import processm.experimental.performance.StandardDistance
import processm.miners.heuristicminer.OfflineHeuristicMiner
import processm.miners.heuristicminer.OnlineHeuristicMiner
import processm.miners.heuristicminer.bindingproviders.BestFirstBindingProvider
import processm.miners.heuristicminer.longdistance.VoidLongDistanceDependencyMiner
import java.io.File
import java.lang.management.ManagementFactory
import java.util.zip.GZIPInputStream
import kotlin.math.pow
import kotlin.random.Random

fun filterLog(base: Log) = Log(base.traces.map { trace ->
    Trace(trace.events
        .filter { it.lifecycleTransition === null || "complete".equals(it.lifecycleTransition, ignoreCase = true) })
})

@InMemoryXESProcessing
class Experiment {

//    private val logs = listOf(
//        "CoSeLoG_WABO_2.xes.gz",
//        "bpi_challenge_2013_open_problems.xes.gz",
//        "BPIC15_2.xes.gz",
//        "Sepsis_Cases-Event_Log.xes.gz",
//        "Hospital_log.xes.gz"
//    ).map { File("xes-logs", it) }
//    private val logs = File("xes-logs").listFiles { dir, name -> name.endsWith(".gz") }.toList()


    private fun load(logfile: File): Log {
        logfile.inputStream().use { base ->
            return filterLog(HoneyBadgerHierarchicalXESInputStream(XMLXESInputStream(GZIPInputStream(base))).first())
        }
    }

    private data class LogStats(val nTraces: Int, val nNames: Int)

    private fun sample(log: Log, random: Random, config: Config): Triple<Log, LogStats, LogStats> {
        val allTraces = log.traces.toList()
        val allNames = allTraces.flatMap { trace -> trace.events.map { it.conceptName }.toSet() }.toSet()
        val completeStats = LogStats(allTraces.size, allNames.size)
        println("# distinct names: ${allNames.size}")
        if (allNames.size <= config.knownNamesThreshold)
            return Triple(log, completeStats, completeStats)
        var knownNames = emptySet<String?>()
        val available = allTraces.indices.toMutableList()
        var ctr = 0
        while (true) {
            val idx = random.nextInt(available.size)
            val trace = allTraces[available[idx]]
            available.removeAt(idx)
            val newKnownNames = knownNames + trace.events.map { it.conceptName }.toSet()
            if (newKnownNames.size > config.knownNamesThreshold) {
                ctr++
                if (ctr < config.missThreshold)
                    continue
                else
                    break
            }
            knownNames = newKnownNames
        }
        println("Selected ${knownNames.size} events: $knownNames")
        val selectedTraces =
            log.traces.filter { trace -> knownNames.containsAll(trace.events.map { it.conceptName }.toList()) }
        println("Selected traces: ${selectedTraces.count()}")
        return Triple(Log(selectedTraces), completeStats, LogStats(selectedTraces.count(), knownNames.size))
    }

    private fun computeOnlineModel(log: Log, maxQueueSize: Int): CausalNet {
        val online = OnlineHeuristicMiner(
            bindingProvider = BestFirstBindingProvider(maxQueueSize = maxQueueSize),
            longDistanceDependencyMiner = VoidLongDistanceDependencyMiner()
        )
        for (trace in log.traces) {
            online.processTrace(trace)
        }
        return online.result
    }

    private fun computeOfflineModel(log: Log, maxQueueSize: Int): CausalNet {
        val offline = OfflineHeuristicMiner(
            bindingProvider = BestFirstBindingProvider(maxQueueSize = maxQueueSize),
            longDistanceDependencyMiner = VoidLongDistanceDependencyMiner()
        )
        offline.processLog(log)
        return offline.result
    }

    private fun cvRanges(n: Int, k: Int): List<Pair<Int, Int>> {
        val step = n / k
        val overhead = n - step * k
        check(overhead < k)
        val ranges = ArrayList<Pair<Int, Int>>()
        var start = 0
        for (i in 0 until k) {
            val end = start + step + if (i < overhead) 1 else 0
            ranges.add(start to end)
            start = end
        }
        check(start == n)
        println(ranges)
        val lengths = ranges.map { (s, e) -> e - s }
        println(lengths)
        return ranges
    }

    private data class Stats(
        val trainFitness: Double, val trainPrecision: Double,
        val testFitness: Double, val testPrecision: Double,
        val resources: ResourceStats
    )

    private fun Collection<Double>.descriptiveStats(): Pair<Double, Double> {
        val mean = this.average()
        val stdev = this.map { (it - mean).pow(2) }.average()
        return mean to stdev
    }

    private val start = object : Event() {
        override var conceptName: String? = "start"
    }
    private val end = object : Event() {
        override var conceptName: String? = "end"
    }

    private fun cvLog(log: Log, k: Int, rnd: Random): Sequence<Pair<Log, Log>> = sequence {
        val allTraces = log.traces.toList().shuffled(rnd)
        for (testRange in cvRanges(allTraces.size, k)) {
            val before = allTraces.subList(0, testRange.first)
            val after = allTraces.subList(testRange.second, allTraces.size)
            val test = allTraces.subList(testRange.first, testRange.second)
            check(before.size + after.size + test.size == allTraces.size)
            val trainLog = Log(before.asSequence() + after.asSequence())
            val testLog = Log(test.map { Trace(sequenceOf(start) + it.events + sequenceOf(end)) }.asSequence())
            yield(trainLog to testLog)
        }
    }

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

    private fun run(log: Pair<Log, Log>, computeModel: (Log) -> CausalNet): Stats {
        val (trainLog, testLog) = log
        var model: CausalNet? = null
        System.gc()
        val time = measureResources { model = computeModel(trainLog) }
        System.gc()
        val trainPA = PerformanceAnalyzer(trainLog, model!!, SkipSpecialForFree(StandardDistance()))
        val testPA = PerformanceAnalyzer(testLog, model!!, SkipSpecialForFree(StandardDistance()))
        System.gc()
        return Stats(trainPA.fitness, trainPA.precision, testPA.fitness, testPA.precision, time)
    }

    private class CSVWriter(file: File, val separator: String = "\t") {

        private val stream = file.outputStream().bufferedWriter()

        operator fun <T> invoke(values: List<T>, vararg key: String) =
            invoke(key.toList() + values.map { it.toString() })

        operator fun invoke(vararg line: Any) = invoke(line.map { it.toString() })

        operator fun invoke(line: List<String>) {
            val text = line.joinToString(separator = separator) { if (it.contains(separator)) "\"$it\"" else it }
            stream.appendln(text)
            stream.flush()
            logger().debug(text)
        }
    }

    @Serializable
    private enum class Mode {
        BATCH, WINDOW
    }

    @Serializable
    private data class Config(
        val logs: List<String>,
        val sampleSeed: Long,
        val cvSeed: Long,
        val k: Int,
        val csv: String,
        val knownNamesThreshold: Int,
        val missThreshold: Int,
        val maxQueueSize: Int,
        val mode: Mode,
        val batchSizes: List<Double>
    ) {
        companion object {
            val json = Json(JsonConfiguration.Default)

            fun load(jsonFile: String): Config {
                return File(jsonFile).bufferedReader().use { return@use json.parse(serializer(), it.readText()) }
            }
        }

        fun save(jsonFile: String) {
            File(jsonFile).bufferedWriter().use { it.write(json.stringify(serializer(), this)) }
        }
    }

    private fun compareBatch(config: Config) {
        val csv = CSVWriter(File(config.csv))
        for (logfile in config.logs.map { File(it) }) {
            val filename = logfile.name
            val completeLog = load(logfile)
            println("${completeLog.traces.count()} $logfile")
            val (partialLog, completeLogStats, partialLogStats) = sample(completeLog, Random(config.sampleSeed), config)
            for ((mode, stats) in listOf("complete" to completeLogStats, "partial" to partialLogStats)) {
                csv(filename, "log", mode, "traces", stats.nTraces)
                csv(filename, "log", mode, "names", stats.nNames)
            }
            val stats = cvLog(partialLog, config.k, Random(config.cvSeed)).map { logpair ->
                val offline = run(logpair) { computeOfflineModel(it, config.maxQueueSize) }
                val online = run(logpair) { computeOnlineModel(it, config.maxQueueSize) }
                return@map offline to online
            }.toList()
            val offlineStats = stats.map { it.first }
            val onlineStats = stats.map { it.second }
            for ((mode, stats) in listOf("offline" to offlineStats, "online" to onlineStats)) {
                csv(stats.map { it.trainFitness }, filename, mode, "train", "fitness")
                csv(stats.map { it.trainPrecision }, filename, mode, "train", "precision")
                csv(stats.map { it.resources.cpuTimeMillis }, filename, mode, "train", "cputimems")
                csv(stats.map { it.resources.peakMemory }, filename, mode, "train", "peakmemory")
                csv(stats.map { it.testFitness }, filename, mode, "test", "fitness")
                csv(stats.map { it.testPrecision }, filename, mode, "test", "precision")
            }
//            println("ONLINE")
//            println("\tfitness ${onlineStats.map { it.testFitness }.descriptiveStats()}")
//            println("\tprecision ${onlineStats.map { it.testPrecision }.descriptiveStats()}")
//            println("\ttime ${onlineStats.map { it.time.toDouble() }.descriptiveStats()}")
        }
    }

    private fun compareWindow(config: Config) {
        val csv = CSVWriter(File(config.csv))
        for (logfile in config.logs.map { File(it) }) {
            val filename = logfile.name
            val completeLog = load(logfile)
            val m = ModelAnalyzer(processTree { null })
            m.playTrace(completeLog.traces.first())
            println("${completeLog.traces.count()} $logfile")
//            val (partialLog, completeLogStats, partialLogStats) = sample(completeLog, Random(config.sampleSeed), config)
//            for ((mode, stats) in listOf("complete" to completeLogStats, "partial" to partialLogStats)) {
//                csv(filename, "log", mode, "traces", stats.nTraces)
//                csv(filename, "log", mode, "names", stats.nNames)
//            }
//            if(partialLogStats.nNames == 0)
//                continue
//            val (trainLog, testLog) = cvLog(partialLog, config.k, Random(config.cvSeed)).first()
//            val trainLogSize = trainLog.traces.count()
//            csv(filename, "log", "train", "traces", trainLogSize)
//            csv(filename, "log", "test", "traces", testLog.traces.count())
//            val trainLog = partialLog
//            val trainLogSize = partialLog.traces.count()
//            for (relativeBatchSize in config.batchSizes) {
//                val batchSize =
//                    (if (relativeBatchSize < 1.0) relativeBatchSize * trainLogSize else relativeBatchSize)
//                        .roundToInt()
//                        .coerceIn(1, trainLogSize)
//                val online = OnlineHeuristicMiner(
//                    bindingProvider = BestFirstBindingProvider(maxQueueSize = config.maxQueueSize),
//                    longDistanceDependencyMiner = VoidLongDistanceDependencyMiner()
//                )
//                val traces = trainLog.traces.toList()
//                val offlineStats = ArrayList<Stats>()
//                val onlineStats = ArrayList<Stats>()
//                for (batchStart in traces.indices step batchSize) {
//                    val batchEnd = min(batchStart + batchSize, traces.size)
//                    val window = traces.subList(0, batchEnd)
//                    System.gc()
//                    var offlineModel: CausalNet? = null
//                    val roff = measureResources {
//                        offlineModel = computeOfflineModel(
//                            Log(window.asSequence()),
//                            config.maxQueueSize
//                        )
//                    }
//                    System.gc()
//                    val ron = measureResources {
//                        for (i in batchStart until batchEnd)
//                            online.processTrace(traces[i])
//                    }
//                    System.gc()
////                    val poff = PerformanceAnalyzer(testLog, offlineModel!!)
////                    val pon = PerformanceAnalyzer(testLog, online.result)
////                    offlineStats.add(Stats(0.0, 0.0, poff.fitness, poff.precision, roff))
////                    onlineStats.add(Stats(0.0, 0.0, pon.fitness, pon.precision, ron))
//                    offlineStats.add(Stats(0.0, 0.0, 0.0, 0.0, roff))
//                    onlineStats.add(Stats(0.0, 0.0, 0.0, 0.0, ron))
//                }
//                csv(*(listOf(filename, "offline", relativeBatchSize, batchSize) + offlineStats.map { it.resources.cpuTimeMillis }).toTypedArray())
//                csv(*(listOf(filename, "online", relativeBatchSize, batchSize) + onlineStats.map { it.resources.cpuTimeMillis }).toTypedArray())
//                println("FITNESS")
//                println("OFFLINE: " + offlineStats.joinToString { it.testFitness.toString() })
//                println("ONLINE: " + onlineStats.joinToString { it.testFitness.toString() })
//                println("PREC")
//                println("OFFLINE: " + offlineStats.joinToString { it.testPrecision.toString() })
//                println("ONLINE: " + onlineStats.joinToString { it.testPrecision.toString() })
//            }
//            val stats = cvLog(partialLog, config.k, Random(config.cvSeed)).map { logpair ->
//                val offline = run(logpair) { computeOfflineModel(it, config.maxQueueSize) }
//                val online = run(logpair) { computeOnlineModel(it, config.maxQueueSize) }
//                return@map offline to online
//            }.toList()
//            val offlineStats = stats.map { it.first }
//            val onlineStats = stats.map { it.second }
//            for ((mode, stats) in listOf("offline" to offlineStats, "online" to onlineStats)) {
//                csv(stats.map { it.trainFitness }, filename, mode, "train", "fitness")
//                csv(stats.map { it.trainPrecision }, filename, mode, "train", "precision")
//                csv(stats.map { it.resources.cpuTimeMillis }, filename, mode, "train", "cputimems")
//                csv(stats.map { it.resources.peakMemory }, filename, mode, "train", "peakmemory")
//                csv(stats.map { it.testFitness }, filename, mode, "test", "fitness")
//                csv(stats.map { it.testPrecision }, filename, mode, "test", "precision")
//            }
//            println("ONLINE")
//            println("\tfitness ${onlineStats.map { it.testFitness }.descriptiveStats()}")
//            println("\tprecision ${onlineStats.map { it.testPrecision }.descriptiveStats()}")
//            println("\ttime ${onlineStats.map { it.time.toDouble() }.descriptiveStats()}")
        }
    }

    fun main(args: Array<String>) {
        val config = Config.load(if (args.isNotEmpty()) args[0] else "config.json")
        println(config)
        if (config.mode == Mode.BATCH)
            compareBatch(config)
        else
            compareWindow(config)
    }
}

@InMemoryXESProcessing
fun main(args: Array<String>) {
    Experiment().main(args)
}
