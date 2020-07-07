package processm.experimental.onlinehmpaper

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import processm.core.log.XMLXESInputStream
import processm.core.log.hierarchical.HoneyBadgerHierarchicalXESInputStream
import processm.core.log.hierarchical.InMemoryXESProcessing
import processm.core.log.hierarchical.Log
import processm.core.log.hierarchical.Trace
import processm.core.models.processtree.ProcessTree
import processm.miners.processtree.inductiveminer.OfflineInductiveMiner
import processm.miners.processtree.inductiveminer.OnlineInductiveMiner
import processm.miners.processtree.inductiveminer.PerformanceAnalyzer
import java.io.File
import java.lang.management.ManagementFactory
import java.util.zip.GZIPInputStream
import kotlin.math.pow
import kotlin.streams.asSequence

fun filterLog(base: Log) = Log(base.traces.map { trace ->
    Trace(trace.events
        .filter { it.lifecycleTransition === null || "complete".equals(it.lifecycleTransition, ignoreCase = true) })
})

@InMemoryXESProcessing
class Experiment {
    private fun load(logfile: File): Log {
        logfile.inputStream().use { base ->
            return filterLog(HoneyBadgerHierarchicalXESInputStream(XMLXESInputStream(GZIPInputStream(base))).first())
        }
    }

    private data class Stats(
        val fitness: Double,
        val precision: Double,
        val resources: ResourceStats
    )

    private data class ResourceStats(val cpuTimeMillis: Long, val peakMemory: Long)

    private fun Collection<Double>.descriptiveStats(): Pair<Double, Double> {
        val mean = this.average()
        val stdev = this.map { (it - mean).pow(2) }.average()
        return mean to stdev
    }

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

    private class CSVWriter(file: File, val separator: String = "\t") {
        private val stream = file.outputStream().bufferedWriter()

        operator fun <T> invoke(values: List<T>, vararg key: String) =
            invoke(key.toList() + values.map { it.toString() })

        operator fun invoke(vararg line: Any) = invoke(line.map { it.toString() })

        operator fun invoke(line: List<String>) {
            val text = line.joinToString(separator = separator) { if (it.contains(separator)) "\"$it\"" else it }
            stream.appendln(text)
            stream.flush()
        }
    }

    @Serializable
    private data class Config(
        val logs: List<String>,
        val windowsSizes: List<Int>,
        val windowsSteps: List<Int>,
        val csv: String,
        val maxTraces: Int
    ) {
        companion object {
            val json = Json(JsonConfiguration.Default)

            fun load(jsonFile: String): Config {
                return File(jsonFile).bufferedReader().use { return@use json.parse(serializer(), it.readText()) }
            }
        }
    }

    private fun logToSequence(
        allTraces: List<Trace>,
        from: Int,
        to: Int
    ): Log {
        return Log(traces = allTraces.take(to).stream().skip(from.toLong()).asSequence())
    }

    private fun compareWindow(config: Config) {
        val csv = CSVWriter(File(config.csv))
        for (logfile in config.logs) {
            val file = File(logfile)
            val wholeLog = load(file)
            // Traces in log (whole log file)
            val allTraces = wholeLog.traces.toList()
            val allTracesSize = allTraces.size
            // Unique activities labels
            val allNames = allTraces.flatMap { trace -> trace.events.map { it.conceptName }.toSet() }.toSet()

            println("File ${file.name}: ${allTracesSize} traces with ${allNames.size} activities")
            csv(file.name, "traces", allTracesSize)
            csv(file.name, "activities", allNames.size)

            for (step in config.windowsSteps) {
                for (windowSize in config.windowsSizes) {
                    println("K = $windowSize \t|\t Step = $step")
                    var current = 0
                    var firstMove = true
                    val imOnline = OnlineInductiveMiner()

                    while (current + windowSize + step < allTracesSize) {
                        println("Window [$current; ${current + windowSize}]")

                        // Offline
                        val imOffline = OfflineInductiveMiner()
                        var modelOffline: ProcessTree? = null

                        // Clean up
                        System.gc()

                        val logInWindow = logToSequence(allTraces, from = current, to = current + windowSize)
                        val timeOffline = measureResources {
                            modelOffline = imOffline.processLog(sequenceOf(logInWindow))
                        }

                        // Clean up
                        System.gc()

                        // Statistics for offline
                        val paOffline = PerformanceAnalyzer(modelOffline!!)
                        logToSequence(
                            allTraces,
                            from = current,
                            to = current + windowSize
                        ).traces.forEach { paOffline.analyze(it) }
                        offlineStats.add(Stats(paOffline.fitness(), paOffline.precision(), timeOffline))

                        // Clean up
                        System.gc()

                        // Online
                        var modelOnline: ProcessTree? = null
                        var timeOnline: ResourceStats

                        // Separate first use and next iterations
                        if (firstMove) {
                            firstMove = false

                            // Clean up
                            System.gc()

                            // Calculate basic model (first)
                            timeOnline = measureResources {
                                modelOnline = imOnline.processLog(
                                    sequenceOf(
                                        logToSequence(
                                            allTraces,
                                            from = current,
                                            to = current + windowSize
                                        )
                                    )
                                )
                            }

                            // Clean up
                            System.gc()
                        } else {
                            // Clean up
                            System.gc()

                            // Prepare changes in DFG
                            val newLog =
                                logToSequence(allTraces, from = current - step + windowSize, to = current + windowSize)
                            val removeLog = logToSequence(allTraces, from = current - step, to = current)

                            // Calculate changes
                            timeOnline = measureResources {
                                imOnline.discover(sequenceOf(removeLog), increaseTraces = false)
                                modelOnline = imOnline.processLog(sequenceOf(newLog))
                            }

                            // Clean up
                            System.gc()
                        }

                        // Statistics for online
                        val paOnline = PerformanceAnalyzer(modelOnline!!)
                        paOnline.cleanNode(modelOnline!!.root!!)
                        logToSequence(
                            allTraces,
                            from = current,
                            to = current + windowSize
                        ).traces.forEach { paOnline.analyze(it) }
                        onlineStats.add(Stats(paOnline.fitness(), paOnline.precision(), timeOnline))

                        // Clean up
                        System.gc()

                        // Step
                        current += step
                    }

                    csv(file.name, "model[buildFromZero]", imOnline.builtFromZero)
                    csv(file.name, "model[rebuild]", imOnline.rebuild)
                    csv(file.name, "model[ignoredRebuild]", imOnline.tracesNoRebuildNeeds)
                }
            }
        }
    }


//                csv(*(listOf(filename, "offline", relativeBatchSize, batchSize) + offlineStats.map { it.resources.cpuTimeMillis }).toTypedArray())
//                csv(*(listOf(filename, "online", relativeBatchSize, batchSize) + onlineStats.map { it.resources.cpuTimeMillis }).toTypedArray())
//                println("FITNESS")
//                println("OFFLINE: " + offlineStats.joinToString { it.testFitness.toString() })
//                println("ONLINE: " + onlineStats.joinToString { it.testFitness.toString() })
//                println("PREC")
//                println("OFFLINE: " + offlineStats.joinToString { it.testPrecision.toString() })
//                println("ONLINE: " + onlineStats.joinToString { it.testPrecision.toString() })
//            }
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

    fun main(args: Array<String>) {
        val config = Config.load("config.json")
        compareWindow(config)
    }
}

@InMemoryXESProcessing
fun main(args: Array<String>) {
    Experiment().main(args)
}
