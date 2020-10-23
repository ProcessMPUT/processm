package processm.experimental.onlinehmpaper

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
import kotlin.random.Random
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

    private fun filterTraces(allTraces: List<Trace>, random: Random, config: Config): List<Trace> {
        val allNames = allTraces.flatMap { trace -> trace.events.map { it.conceptName }.toSet() }.toSet()
        println("# distinct names: ${allNames.size}")
        if (allNames.size <= config.maxActivities)
            return allTraces
        var knownNames = emptySet<String?>()
        val available = allTraces.indices.toMutableList()
        var ctr = 0
        while (true) {
            val idx = random.nextInt(available.size)
            val trace = allTraces[available[idx]]
            available.removeAt(idx)
            val newKnownNames = knownNames + trace.events.map { it.conceptName }.toSet()
            if (newKnownNames.size > config.maxActivities) {
                ctr++
                if (ctr < 10)
                    continue
                else
                    break
            }
            knownNames = newKnownNames
        }
        println("Selected ${knownNames.size} events: $knownNames")
        val selectedTraces =
            allTraces.filter { trace -> knownNames.containsAll(trace.events.map { it.conceptName }.toList()) }
        println("Selected traces: ${selectedTraces.count()}")
        return selectedTraces
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
            stream.appendLine(text)
            stream.flush()
        }
    }

    @Serializable
    private data class Config(
        val logs: List<String>,
        val windowsSizes: List<Int>,
        val windowsSteps: List<Int>,
        val csv: String,
        val maxActivities: Int,
        val seed: Int
    ) {
        companion object {
            val json = Json { allowStructuredMapKeys = true }

            fun load(jsonFile: String): Config {
                return File(jsonFile).bufferedReader()
                    .use { return@use json.decodeFromString(serializer(), it.readText()) }
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
            var allTraces = wholeLog.traces.toList()
            // Unique activities labels
            val allNames = allTraces.flatMap { trace -> trace.events.map { it.conceptName }.toSet() }.toSet()

            println("File ${file.name}: ${allTraces.size} traces with ${allNames.size} activities")
            csv(file.name, "traces", allTraces.size)
            csv(file.name, "activities", allNames.size)

            allTraces = filterTraces(allTraces, Random(seed = config.seed), config)
            val allTracesSize = allTraces.size

            for (step in config.windowsSteps) {
                for (windowSize in config.windowsSizes) {
                    println("K = $windowSize \t|\t Step = $step")
                    var current = 0
                    var firstMove = true
                    val imOnline = OnlineInductiveMiner()

                    do {
                        println("Window [$current; ${current + windowSize}]")

                        // Offline
                        val imOffline = OfflineInductiveMiner()
                        imOffline.useStatistics = false
                        var modelOffline: ProcessTree? = null

                        // Clean up
                        System.gc()

                        val logInWindow = logToSequence(allTraces, from = current, to = current + windowSize)
                        val timeOffline = measureResources {
                            modelOffline = imOffline.processLog(sequenceOf(logInWindow))
                        }
                        println(modelOffline)

                        // Clean up
                        System.gc()

                        // Statistics for offline
                        val paOffline = PerformanceAnalyzer(modelOffline!!)
                        logToSequence(
                            allTraces,
                            from = current,
                            to = current + windowSize
                        ).traces.forEach { paOffline.analyze(it) }
                        csv(file.name, "fitnessTrain", "offline", windowSize, step, current, paOffline.fitness())
                        csv(file.name, "precisionTrain", "offline", windowSize, step, current, paOffline.precision())
                        paOffline.cleanNode(modelOffline!!.root!!)
                        logToSequence(
                            allTraces,
                            from = current + windowSize,
                            to = current + (windowSize * 2)
                        ).traces.forEach { paOffline.analyze(it) }
                        csv(file.name, "fitnessTest", "offline", windowSize, step, current, paOffline.fitness())
                        csv(file.name, "precisionTest", "offline", windowSize, step, current, paOffline.precision())
                        csv(file.name, "time", "offline", windowSize, step, current, timeOffline.cpuTimeMillis)
                        csv(file.name, "memory", "offline", windowSize, step, current, timeOffline.peakMemory)
                        csv(file.name, "model", "offline", windowSize, step, current, modelOffline.toString())

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
                                imOnline.discover(sequenceOf(newLog), increaseTraces = true)
                                modelOnline = imOnline.processLog(sequenceOf(removeLog), increaseTraces = false)
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
                        csv(file.name, "fitnessTrain", "online", windowSize, step, current, paOnline.fitness())
                        csv(file.name, "precisionTrain", "online", windowSize, step, current, paOnline.precision())
                        paOnline.cleanNode(modelOnline!!.root!!)
                        logToSequence(
                            allTraces,
                            from = current + windowSize,
                            to = current + (windowSize * 2)
                        ).traces.forEach { paOnline.analyze(it) }
                        csv(file.name, "fitnessTest", "online", windowSize, step, current, paOnline.fitness())
                        csv(file.name, "precisionTest", "online", windowSize, step, current, paOnline.precision())
                        csv(file.name, "time", "online", windowSize, step, current, timeOnline.cpuTimeMillis)
                        csv(file.name, "memory", "online", windowSize, step, current, timeOnline.peakMemory)
                        csv(file.name, "model", "online", windowSize, step, current, modelOnline.toString())

                        // Clean up
                        System.gc()

                        // Step
                        current += step

                        println("${imOnline.builtFromZero} ${imOnline.rebuild} ${imOnline.tracesNoRebuildNeeds}")
                    } while (current + windowSize - step < allTracesSize)

                    csv(file.name, "modelBuildFromZero]", "online", imOnline.builtFromZero)
                    csv(file.name, "modelRebuild", "online", imOnline.rebuild)
                    csv(file.name, "modelIgnoredRebuild", "online", imOnline.tracesNoRebuildNeeds)
                }
            }
        }
    }

    fun main(args: Array<String>) {
        val config = Config.load("config.json")
        compareWindow(config)
    }
}

@InMemoryXESProcessing
fun main(args: Array<String>) {
    Experiment().main(args)
}