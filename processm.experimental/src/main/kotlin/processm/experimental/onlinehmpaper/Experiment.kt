package processm.experimental.onlinehmpaper

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import processm.core.log.XMLXESInputStream
import processm.core.log.XMLXESOutputStream
import processm.core.log.hierarchical.*
import processm.core.models.processtree.ProcessTree
import processm.miners.processtree.inductiveminer.OfflineInductiveMiner
import processm.miners.processtree.inductiveminer.OnlineInductiveMiner
import processm.miners.processtree.inductiveminer.PerformanceAnalyzer
import java.io.File
import java.io.FileOutputStream
import java.lang.management.ManagementFactory
import java.util.zip.GZIPInputStream
import javax.xml.stream.XMLOutputFactory
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
        val maxActivities: Int,
        val seed: Int,
        val iteration: String
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

    private fun log2File(log: Log, mode: String) {
        FileOutputStream("logFile-$mode.xes").use { received ->
            val writer = XMLXESOutputStream(XMLOutputFactory.newInstance().createXMLStreamWriter(received))

            writer.write(log.toFlatSequence())
            writer.close()
        }
    }

    private fun compareWindow(config: Config) {
        for (logfile in config.logs) {
            val file = File(logfile)
            val name = file.name.removeSuffix(".xes.gz")

            // Read log file
            val wholeLog = load(file)
            // Traces in log (whole log file)
            val allTraces = filterTraces(wholeLog.traces.toList(), Random(seed = config.seed), config)
            val allTracesSize = allTraces.size

            println("---------------- $name: $allTracesSize traces ---------------- ")

            val onlineExtraStats = CSVWriter(File("${config.iteration}-online-extra-$name"))
            val offlineStats = CSVWriter(File("${config.iteration}-offlinetrue-$name"))
            val offlineNoStats = CSVWriter(File("${config.iteration}-offlinefalse-$name"))
            val onlineStats = CSVWriter(File("${config.iteration}-online-stats-$name"))

            for (step in config.windowsSteps) {
                for (windowSize in config.windowsSizes) {
                    var current = 0
                    var firstMove = true
                    val imOnline = OnlineInductiveMiner()

                    do {
                        println("[FILE=${name}][$current; ${current + windowSize}]")

                        // Store train log file
                        log2File(
                            logToSequence(
                                allTraces,
                                from = current,
                                to = current + windowSize
                            ), "train"
                        )

                        // Store test log file
                        log2File(
                            logToSequence(
                                allTraces,
                                from = current + windowSize,
                                to = current + (windowSize * 2)
                            ), "test"
                        )

                        // Offline without stats
                        calcOffline(
                            allTraces,
                            current,
                            step,
                            windowSize,
                            offlineStats,
                            name,
                            config.iteration,
                            useStatsMode = false
                        )

                        // Offline with stats
                        calcOffline(
                            allTraces,
                            current,
                            step,
                            windowSize,
                            offlineNoStats,
                            name,
                            config.iteration,
                            useStatsMode = true
                        )

                        // Online
                        calcOnline(
                            imOnline,
                            allTraces,
                            current,
                            step,
                            windowSize,
                            onlineStats,
                            name,
                            config.iteration,
                            firstMove
                        )
                        firstMove = false

                        // Step
                        current += step
                    } while (current + windowSize - step < allTracesSize)

                    onlineExtraStats(windowSize, step, imOnline.builtFromZero, imOnline.rebuild, imOnline.tracesNoRebuildNeeds)
                }
            }
        }
    }

    private fun calcOnline(
        imOnline: OnlineInductiveMiner,
        allTraces: List<Trace>,
        current: Int,
        step: Int,
        windowSize: Int,
        csv: CSVWriter,
        xesFile: String,
        iteration: String,
        firstMove: Boolean
    ) {
        // Clean up
        System.gc()

        var modelOnline: ProcessTree? = null
        val timeOnline: ResourceStats

        // Separate first use and next iterations
        if (firstMove) {
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
        } else {
            // Prepare changes in DFG
            val newLog = logToSequence(allTraces, from = current - step + windowSize, to = current + windowSize)
            val removeLog = logToSequence(allTraces, from = current - step, to = current)

            // Calculate changes
            timeOnline = measureResources {
                imOnline.discover(sequenceOf(newLog), increaseTraces = true)
                modelOnline = imOnline.processLog(sequenceOf(removeLog), increaseTraces = false)
            }
        }

        csv("time", "online", windowSize, step, current, timeOnline.cpuTimeMillis)
        csv("memory", "online", windowSize, step, current, timeOnline.peakMemory)
        csv("model", "online", windowSize, step, current, modelOnline.toString())

        FileOutputStream("onlineModel.tree").use { file ->
            modelOnline!!.toPTML(XMLOutputFactory.newInstance().createXMLStreamWriter(file))
        }
        val train = Runtime.getRuntime()
            .exec("python3 tree_stats.py $xesFile onlineModel.tree $iteration online train $windowSize $step $current")
        train.waitFor()

        val test = Runtime.getRuntime()
            .exec("python3 tree_stats.py $xesFile onlineModel.tree $iteration online test $windowSize $step $current")
        test.waitFor()

        // Clean up
        System.gc()
    }

    private fun calcOffline(
        allTraces: List<Trace>,
        current: Int,
        step: Int,
        windowSize: Int,
        csv: CSVWriter,
        xesFile: String,
        iteration: String,
        useStatsMode: Boolean = true
    ) {
        // Clean up
        System.gc()

        val imOffline = OfflineInductiveMiner()
        imOffline.useStatistics = useStatsMode
        var modelOffline: ProcessTree? = null

        val logInWindow = logToSequence(allTraces, from = current, to = current + windowSize)
        val timeOffline = measureResources {
            modelOffline = imOffline.processLog(sequenceOf(logInWindow))
        }

        csv("time", "offline$useStatsMode", windowSize, step, current, timeOffline.cpuTimeMillis)
        csv("memory", "offline$useStatsMode", windowSize, step, current, timeOffline.peakMemory)
        csv("model", "offline$useStatsMode", windowSize, step, current, modelOffline.toString())

        FileOutputStream("offline$useStatsMode.tree").use { file ->
            modelOffline!!.toPTML(XMLOutputFactory.newInstance().createXMLStreamWriter(file))
        }
        val train = Runtime.getRuntime()
            .exec("python3 tree_stats.py $xesFile offline$useStatsMode.tree $iteration offline$useStatsMode train $windowSize $step $current")
        train.waitFor()

        val test = Runtime.getRuntime()
            .exec("python3 tree_stats.py $xesFile offline$useStatsMode.tree $iteration offline$useStatsMode test $windowSize $step $current")
        test.waitFor()

        // Clean up
        System.gc()
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