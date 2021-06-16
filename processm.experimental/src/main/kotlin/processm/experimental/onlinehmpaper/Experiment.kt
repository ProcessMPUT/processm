package processm.experimental.onlinehmpaper

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import processm.core.log.Event
import processm.core.log.XMLXESInputStream
import processm.core.log.XMLXESOutputStream
import processm.core.log.hierarchical.*
import processm.core.logging.logger
import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.toDSL
import processm.experimental.performance.PerformanceAnalyzer
import processm.experimental.performance.SkipSpecialForFree
import processm.experimental.performance.StandardDistance
import processm.miners.causalnet.heuristicminer.OfflineHeuristicMiner
import processm.experimental.miners.causalnet.heuristicminer.OnlineHeuristicMiner
import processm.miners.causalnet.heuristicminer.bindingproviders.BestFirstBindingProvider
import processm.miners.causalnet.heuristicminer.dependencygraphproviders.BasicDependencyGraphProvider
import processm.miners.causalnet.heuristicminer.dependencygraphproviders.DefaultDependencyGraphProvider
import processm.miners.causalnet.heuristicminer.longdistance.VoidLongDistanceDependencyMiner
import processm.miners.causalnet.onlineminer.OnlineMiner
import java.io.File
import java.io.PrintStream
import java.lang.management.ManagementFactory
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import javax.xml.stream.XMLOutputFactory
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

fun filterLog(base: Log) = Log(base.traces.map { trace ->
    Trace(trace.events
        .filter { it.lifecycleTransition === null || "complete".equals(it.lifecycleTransition, ignoreCase = true) })
})

@ExperimentalStdlibApi
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


    data class Key(val logfile: String, val windowSize: Int, val logidx: Int, val traceidx: Int) {
        val modelFileName: String
            get() = "model_${logfile}_${windowSize}_${logidx}_${traceidx}.pnml"
        val trainFileName: String
            get() = "train_${logfile}_${windowSize}_${logidx}_${traceidx}.xes"
        val testFileName: String
            get() = "test_${logfile}_${windowSize}_${logidx}_${traceidx}.xes"
    }

    companion object {
        private val logger = logger()
    }

    private fun load(logfile: File): Log {
        logfile.inputStream().use { base ->
            return filterLog(HoneyBadgerHierarchicalXESInputStream(XMLXESInputStream(GZIPInputStream(base))).first())
        }
    }

    private fun dependencyGraphProvider(minDependency: Double?) =
        if (minDependency != null)
            DefaultDependencyGraphProvider(1, minDependency)
        else BasicDependencyGraphProvider(1)

    private fun computeOnlineModel(log: Log, maxQueueSize: Int, minDependency: Double?): CausalNet {
        val online = OnlineHeuristicMiner(
            dependencyGraphProvider = dependencyGraphProvider(minDependency),
            bindingProvider = BestFirstBindingProvider(maxQueueSize = maxQueueSize),
            longDistanceDependencyMiner = VoidLongDistanceDependencyMiner()
        )
        for (trace in log.traces) {
            online.processTrace(trace)
        }
        return online.result
    }

    private fun computeOfflineModel(log: Log, maxQueueSize: Int, minDependency: Double?): CausalNet {
        val offline = OfflineHeuristicMiner(
            dependencyGraphProvider = dependencyGraphProvider(minDependency),
            bindingProvider = BestFirstBindingProvider(maxQueueSize = maxQueueSize),
            longDistanceDependencyMiner = VoidLongDistanceDependencyMiner()
        )
        offline.processLog(log)
        return offline.result
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
//        val trainPA = PerformanceAnalyzer(trainLog, model!!, SkipSpecialForFree(StandardDistance()))
        val testPA = PerformanceAnalyzer(testLog, model!!, SkipSpecialForFree(StandardDistance()))
        System.gc()
        //return Stats(trainPA.fitness, trainPA.precision, testPA.fitness, testPA.precision, time)
        return Stats(Double.NaN, Double.NaN, testPA.fitness, testPA.precision, time)
    }

    private class CSVWriter(file: File, val separator: String = "\t") {

        private val stream = file.outputStream().bufferedWriter()

        operator fun <T> invoke(values: List<T>, key: Key) =
            invoke((listOf(key.logfile, key.windowSize, key.logidx, key.traceidx) + values).map { it.toString() })

        operator fun invoke(vararg line: Any?) = invoke(line.map { it.toString() })

        operator fun invoke(line: List<String>) {
            val text = line.joinToString(separator = separator) { if (it.contains(separator)) "\"$it\"" else it }
            stream.appendLine(text)
            stream.flush()
            logger().debug(text)
        }
    }

    @Serializable
    internal enum class Mode {
        BATCH, WINDOW, DRIFT, PERFORMANCE
    }

    @Serializable
    internal enum class Measure {
        TRAIN_FITNESS, TRAIN_PRECISION, TEST_FITNESS, TEST_PRECISION, TRAIN_PFR, TEST_PFR, TRAIN_RF, TEST_RF
    }

    @Serializable
    internal data class Config(
        val logs: List<String>,
        val splitSeed: Long,
        val sampleSeed: Long,
        val cvSeed: Long,
        val kfit: Int,
        val keval: Int,
        val csv: String,
        val knownNamesThreshold: Int,
        val missThreshold: Int,
        val maxQueueSize: Int,
        val mode: Mode,
        val batchSizes: List<Double>,
        val minDependency: List<Double>,
        val measures: List<Measure> = listOf(Measure.TEST_FITNESS, Measure.TEST_PRECISION),
        val maxVisitedCoefficient: Int = 100,
        val artifacts: String? = null,
        val rangeFitnessTimeout: Long = -1
    ) {
        companion object {
            val json = Json.Default

            fun load(jsonFile: String): Config {
                return File(jsonFile).bufferedReader().use {
                    return@use json.decodeFromString(
                        serializer(),
                        it.readText()
                    )
                }
            }
        }

        fun save(jsonFile: String) {
            File(jsonFile).bufferedWriter().use { it.write(json.encodeToString(serializer(), this)) }
        }
    }

    private fun compareBatch(config: Config) {
        val csv = CSVWriter(File(config.csv))
        for (logfile in config.logs.map { File(it) }) {
            val filename = logfile.name
            val completeLog = load(logfile)
            println("${completeLog.traces.count()} $logfile")
            val (partialLog, completeLogStats, partialLogStats) = sample(
                completeLog,
                Random(config.sampleSeed),
                config.knownNamesThreshold,
                config.missThreshold
            )
            for ((mode, stats) in listOf("complete" to completeLogStats, "partial" to partialLogStats)) {
                csv(filename, "log", mode, "traces", stats.nTraces)
                csv(filename, "log", mode, "names", stats.nNames)
            }
            val (bestMinDependency, evalLog) = if (config.minDependency.size > 1) {
                val (fitLog, evalLog) = cvLog(partialLog, 2, Random(config.splitSeed), start, end).first()
                csv(filename, "log", "fit", "traces", fitLog.traces.count())
                csv(filename, "log", "eval", "traces", evalLog.traces.count())
                val minDependencyFittingStats = config.minDependency.map { minDependency ->
                    minDependency to cvLog(fitLog, config.kfit, Random(config.cvSeed), start, end).map { logpair ->
                        try {
                            val model = computeOfflineModel(logpair.first, config.maxQueueSize, minDependency)
                            val pa = PerformanceAnalyzer(logpair.second, model, SkipSpecialForFree(StandardDistance()))
                            return@map 2.0 / (1.0 / pa.precision + 1.0 / pa.fitness)
                        } catch (e: IllegalStateException) {
                            return@map Double.NaN
                        }
                    }.toList()
                }
                println(minDependencyFittingStats)
                for (row in minDependencyFittingStats)
                    csv(row.second, filename, "offline", "fit", row.first.toString())
                minDependencyFittingStats.maxBy { it.second.average() }!!.first to evalLog
            } else
                (if (config.minDependency.size == 1) config.minDependency.single() else null) to partialLog
            csv(filename, "offline", "fit", "best", bestMinDependency)
            val stats = cvLog(evalLog, config.keval, Random(config.cvSeed), start, end).map { logpair ->
                val offline = run(logpair) { computeOfflineModel(it, config.maxQueueSize, bestMinDependency) }
                val online = run(logpair) { computeOnlineModel(it, config.maxQueueSize, bestMinDependency) }
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
            println("${completeLog.traces.count()} $logfile")
            val (partialLog, completeLogStats, partialLogStats) = sample(
                completeLog,
                Random(config.sampleSeed),
                config.knownNamesThreshold,
                config.missThreshold
            )
            for ((mode, stats) in listOf("complete" to completeLogStats, "partial" to partialLogStats)) {
                csv(filename, "log", mode, "traces", stats.nTraces)
                csv(filename, "log", mode, "names", stats.nNames)
            }
            if (partialLogStats.nNames == 0)
                continue
//            val (trainLog, testLog) = cvLog(partialLog, config.k, Random(config.cvSeed)).first()
//            val trainLogSize = trainLog.traces.count()
//            csv(filename, "log", "train", "traces", trainLogSize)
//            csv(filename, "log", "test", "traces", testLog.traces.count())
            val trainLog = partialLog
            val trainLogSize = partialLog.traces.count()
            for (relativeBatchSize in config.batchSizes) {
                val batchSize =
                    (if (relativeBatchSize < 1.0) relativeBatchSize * trainLogSize else relativeBatchSize)
                        .roundToInt()
                        .coerceIn(1, trainLogSize)
                val online = OnlineHeuristicMiner(
                    bindingProvider = BestFirstBindingProvider(maxQueueSize = config.maxQueueSize),
                    longDistanceDependencyMiner = VoidLongDistanceDependencyMiner()
                )
                val traces = trainLog.traces.toList()
                val offlineStats = ArrayList<Stats>()
                val onlineStats = ArrayList<Stats>()
                for (batchStart in traces.indices step batchSize) {
                    val batchEnd = min(batchStart + batchSize, traces.size)
                    val window = traces.subList(0, batchEnd)
                    System.gc()
                    var offlineModel: CausalNet? = null
                    val roff = measureResources {
                        offlineModel = computeOfflineModel(
                            Log(window.asSequence()),
                            config.maxQueueSize,
                            Double.NEGATIVE_INFINITY
                        )
                    }
                    System.gc()
                    val ron = measureResources {
                        for (i in batchStart until batchEnd)
                            online.processTrace(traces[i])
                    }
                    System.gc()
//                    val poff = PerformanceAnalyzer(testLog, offlineModel!!)
//                    val pon = PerformanceAnalyzer(testLog, online.result)
//                    offlineStats.add(Stats(0.0, 0.0, poff.fitness, poff.precision, roff))
//                    onlineStats.add(Stats(0.0, 0.0, pon.fitness, pon.precision, ron))
                    offlineStats.add(Stats(0.0, 0.0, 0.0, 0.0, roff))
                    onlineStats.add(Stats(0.0, 0.0, 0.0, 0.0, ron))
                }
                csv(
                    *(listOf(
                        filename,
                        "offline",
                        relativeBatchSize,
                        batchSize
                    ) + offlineStats.map { it.resources.cpuTimeMillis }).toTypedArray()
                )
                csv(
                    *(listOf(
                        filename,
                        "online",
                        relativeBatchSize,
                        batchSize
                    ) + onlineStats.map { it.resources.cpuTimeMillis }).toTypedArray()
                )
//                println("FITNESS")
//                println("OFFLINE: " + offlineStats.joinToString { it.testFitness.toString() })
//                println("ONLINE: " + onlineStats.joinToString { it.testFitness.toString() })
//                println("PREC")
//                println("OFFLINE: " + offlineStats.joinToString { it.testPrecision.toString() })
//                println("ONLINE: " + onlineStats.joinToString { it.testPrecision.toString() })
            }
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

    internal fun performance(config: Config) {
        val threadMXBean = ManagementFactory.getThreadMXBean()
        threadMXBean.isThreadCpuTimeEnabled = true
        val csv = CSVWriter(File(config.csv))
        for (windowSize in config.batchSizes.map { it.toInt() }) {
            for (logfile in config.logs.map { File(it) }) {
                val filename = logfile.name
                val partialLogs = try {
                    createDriftLogs(
                        load(logfile),
                        config.sampleSeed,
                        config.splitSeed,
                        config.keval,
                        config.knownNamesThreshold,
                        config.missThreshold
                    )
                } catch (e: IllegalStateException) {
                    logger().warn(filename, e)
                    continue
                }
                println("Sublog sizes: ${partialLogs.map { it.size }}")
                val online = OnlineMiner()
                val log = partialLogs.mapIndexed { logidx, log ->
                    log.mapIndexed { traceidx, trace ->
                        Triple(logidx, traceidx, trace)
                    }
                }.flatten()
                println("window size=$windowSize")
                for (i in 0 until log.size) {
                    println("i=$i")
                    val (logidx, traceidx, trace) = log[i]
                    val key = Key(filename, windowSize, logidx, traceidx)
                    val addLog = Log(sequenceOf(trace))
                    val removeLog = Log(if (i >= windowSize) sequenceOf(log[i - windowSize].third) else emptySequence())
                    val trainingTraces = log.subList((i - windowSize + 1).coerceAtLeast(0), i + 1).map { it.third }
                    assert((i+1 < windowSize && trainingTraces.size == i+1) || (trainingTraces.size == windowSize)) {"i=$i #trainingTraces=${trainingTraces.size}"}
                    val trainingLog = Log(trainingTraces.asSequence())

                    var onlineTime = threadMXBean.currentThreadCpuTime
                    online.processDiff(addLog, removeLog)
                    onlineTime = threadMXBean.currentThreadCpuTime - onlineTime

                    val offline = OnlineMiner()
                    var offlineTime = threadMXBean.currentThreadCpuTime
                    offline.processLog(trainingLog)
                    offlineTime = threadMXBean.currentThreadCpuTime - offlineTime

                    csv(listOf(onlineTime, offlineTime), key)
                }
            }
        }
    }

    internal fun drift(config: Config) {
        val pool = ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors(),
            Runtime.getRuntime().availableProcessors(),
            60,
            TimeUnit.SECONDS,
            LinkedBlockingQueue()
        )
//        val astarFactory = CachingAlignerFactory(DefaultAlignmentCache()) { m, p, _ -> AStar(m, p) }
        val csv = CSVWriter(File(config.csv))
        val jsFile = File("models.js").outputStream().bufferedWriter()
        for (windowSize in config.batchSizes.map { it.toInt() }) {
            for (logfile in config.logs.map { File(it) }) {
                val filename = logfile.name
                val partialLogs = try {
                    createDriftLogs(
                        load(logfile),
                        config.sampleSeed,
                        config.splitSeed,
                        config.keval,
                        config.knownNamesThreshold,
                        config.missThreshold
                    )
                } catch (e: IllegalStateException) {
                    logger().warn(filename, e)
                    continue
                }
                println("Sublog sizes: ${partialLogs.map { it.size }}")
                val online = OnlineMiner()
                val log = partialLogs.mapIndexed { logidx, log ->
                    log.mapIndexed { traceidx, trace ->
                        Triple(logidx, traceidx, trace)
                    }
                }.flatten()
                println("window size=$windowSize")
                for (i in 0 until log.size) {
                    println("i=$i")
                    val (logidx, traceidx, trace) = log[i]
                    val key = Key(filename, windowSize, logidx, traceidx)
                    val addLog = Log(sequenceOf(trace))
                    val removeLog = Log(if (i >= windowSize) sequenceOf(log[i - windowSize].third) else emptySequence())
                    online.processDiff(addLog, removeLog)
                    val trainLog = if (i + 1 >= windowSize) {
                        val trainTraces = log.subList(i - windowSize + 1, i + 1).map { it.third }
                        check(trainTraces.size == windowSize)
                        check(trace in trainTraces)
                        Log(trainTraces.asSequence())
                    } else
                        null
                    val testLog = if (i + windowSize + 1 <= log.size) {
                        val testTraces = log.subList(i + 1, i + 1 + windowSize).map { it.third }
                        check(testTraces.size == windowSize)
                        Log(testTraces.asSequence())
                    } else
                        null
                    if (config.artifacts != null) {
                        val dir = File(config.artifacts)
                        if (!dir.exists())
                            dir.mkdirs()
                        check(dir.isDirectory)
                        File(dir, key.modelFileName).outputStream().use {
                            online.result.toPM4PY(it)
                        }
                        File(dir, key.modelFileName + ".kt").outputStream().use { f ->
                            PrintStream(f).use { p ->
                                p.println(online.result.toDSL())
                            }
                        }
                        if (trainLog != null)
                            File(dir, key.trainFileName).outputStream().use { fileStream ->
                                XMLXESOutputStream(
                                    XMLOutputFactory.newInstance().createXMLStreamWriter(fileStream)
                                ).use {
                                    it.write(trainLog.toFlatSequence())
                                }
                            }
                        if (testLog != null)
                            File(dir, key.testFileName).outputStream().use { fileStream ->
                                XMLXESOutputStream(
                                    XMLOutputFactory.newInstance().createXMLStreamWriter(fileStream)
                                ).use {
                                    it.write(testLog.toFlatSequence())
                                }
                            }
                    }

                    val values = List(4) { Double.NaN }.toMutableList()
//                    val aligner = DecompositionAligner(online.result.toPetriNet(), alignerFactory= astarFactory, pool=pool)
//                    val rf = RangeFitness(aligner, 100, TimeUnit.MILLISECONDS)
//                    if(trainLog != null) {
//                        val rfvalue = rf(trainLog)
//                        values[0] = rfvalue.start
//                        values[1] = rfvalue.endInclusive
//                    }

//                    if(testLog!=null) {
//                        val rfvalue = rf(testLog)
//                        values[2] = rfvalue.start
//                        values[3] = rfvalue.endInclusive
//                    }

//                    val fa = PerfectAligner(online.result)
//                    val values = List(6) { Double.NaN }.toMutableList()
//                    if (trainLog != null) {
//                        if (Measure.TRAIN_PFR in config.measures)
//                            values[0] =
//                                fa.perfectFitRatio(trainLog)    // train pfr doesn't use maxVisitedCoefficient to always generate accurate results
//                        if (Measure.TRAIN_FITNESS in config.measures || Measure.TRAIN_PRECISION in config.measures) {
//                            val pa =
//                                PerformanceAnalyzer(trainLog, online.result, SkipSpecialForFree(StandardDistance()))
//                            values[1] = if (Measure.TRAIN_FITNESS in config.measures) pa.fitness else Double.NaN
//                            values[2] = if (Measure.TRAIN_PRECISION in config.measures) pa.precision else Double.NaN
//                        }
//                    }
//                    if (testLog != null) {
//                        if (Measure.TEST_PFR in config.measures)
//                            values[3] =
//                                fa.perfectFitRatio(testLog, config.maxVisitedCoefficient)
//                        if (Measure.TEST_FITNESS in config.measures || Measure.TEST_PRECISION in config.measures) {
//                            val pa = PerformanceAnalyzer(testLog, online.result, SkipSpecialForFree(StandardDistance()))
//                            values[4] = if (Measure.TEST_FITNESS in config.measures) pa.fitness else Double.NaN
//                            values[5] = if (Measure.TEST_PRECISION in config.measures) pa.precision else Double.NaN
//                        }
//                    }
                    csv(values, key)
                }
            }
        }
    }

    fun main(args: Array<String>) {
        val config = Config.load(if (args.isNotEmpty()) args[0] else "config.json")
        println(config)
        if (config.mode == Mode.BATCH)
            compareBatch(config)
        else if (config.mode == Mode.DRIFT)
            drift(config)
        else if(config.mode == Mode.PERFORMANCE)
        else
            compareWindow(config)
    }
}

@ExperimentalStdlibApi
@InMemoryXESProcessing
fun main(args: Array<String>) {
    Experiment().main(args)
}
