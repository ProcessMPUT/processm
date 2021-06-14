package processm.experimental.onlinehmpaper

import ch.qos.logback.classic.Level
import kotlinx.serialization.json.Json
import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory
import processm.core.log.XMLXESInputStream
import processm.core.log.hierarchical.HoneyBadgerHierarchicalXESInputStream
import processm.core.log.hierarchical.InMemoryXESProcessing
import processm.core.log.hierarchical.Log
import processm.miners.onlineminer.OnlineMiner
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@InMemoryXESProcessing
@ExperimentalStdlibApi
class ExperimentTest {

    @Test
    fun `CoSeLoG_WABO_2`() {
        val artifacts = createTempDir()
        println(artifacts.absolutePath)
        val temp = createTempFile()
        temp.deleteOnExit()
        val configText = """
            {
                    "logs": [
                      "../xes-logs/CoSeLoG_WABO_2.xes.gz"
                    ],
                    "splitSeed": 3737844653,
                    "sampleSeed": 12648430,
                    "cvSeed": 4276993775,
                    "keval": 5,
                    "kfit": 5,
                    "csv": "${temp.absolutePath}",
                    "knownNamesThreshold": 100,
                    "missThreshold": 10,
                    "maxQueueSize": 100,
                    "mode": "DRIFT",
                    "batchSizes": [25],
                    "minDependency": [0], 
                    "measures": ["TRAIN_PFR", "TEST_PFR"],
                    "artifacts": "${artifacts.absolutePath}"
            }

        """.trimIndent()
        val config = Json.Default.decodeFromString(Experiment.Config.serializer(), configText)
        println(config)
        Experiment().drift(config)
        temp.useLines { lines ->
            for ((i, line) in lines.withIndex()) {
                if (line.trim().isEmpty())
                    continue
                val row = line.split("\t")
                println("$i $row")
                assertEquals("CoSeLoG_WABO_2.xes.gz", row[0])
                assertEquals(25, row[1].toInt())
                assertEquals(if (i < 24) Double.NaN else 1.0, row[4].toDouble())
                assertEquals(Double.NaN, row[5].toDouble())
                assertEquals(Double.NaN, row[6].toDouble())
                val testPFR = row[7].toDouble()
                if (i < 78) {
                    assertTrue { 0.0 <= testPFR }
                    assertTrue { testPFR <= 1.0 }
                } else
                    assertEquals(Double.NaN, testPFR)
                assertEquals(Double.NaN, row[8].toDouble())
                assertEquals(Double.NaN, row[9].toDouble())
            }
        }
    }

    @Test
    fun `Receipt_phase_of_an_environmental_permit_application_process_WABO_CoSeLoG_project`() {
        val artifacts = createTempDir()
        val temp = createTempFile()
        try {
            val configText = """
            {
                    "logs": [
                      "../xes-logs/Receipt_phase_of_an_environmental_permit_application_process_WABO_CoSeLoG_project.xes.gz"
                    ],
                    "splitSeed": 3737844653,
                    "sampleSeed": 12648430,
                    "cvSeed": 4276993775,
                    "keval": 5,
                    "kfit": 5,
                    "csv": "${temp.absolutePath}",
                    "knownNamesThreshold": 100,
                    "missThreshold": 10,
                    "maxQueueSize": 100,
                    "mode": "DRIFT",
                    "batchSizes": [5, 10],
                    "minDependency": [0],
                    "measures": ["TRAIN_PFR", "TEST_PFR"],
                    "artifacts": "${artifacts.absolutePath}"
            }

        """.trimIndent()
            val config = Json.Default.decodeFromString(Experiment.Config.serializer(), configText)
            println(config)
            Experiment().drift(config)
            temp.useLines { lines ->
                for ((i, line) in lines.withIndex()) {
                    if (line.trim().isEmpty())
                        continue
                    val row = line.split("\t")
                    println("$i $row")
                    assertEquals(
                        "Receipt_phase_of_an_environmental_permit_application_process_WABO_CoSeLoG_project.xes.gz",
                        row[0]
                    )
                    assertTrue(row[1].toInt() in setOf(5, 10))
                    val trainPFR = row[4].toDouble()
                    assertTrue(trainPFR in setOf(Double.NaN, 1.0))
                    assertEquals(Double.NaN, row[5].toDouble())
                    assertEquals(Double.NaN, row[6].toDouble())
                    val testPFR = row[7].toDouble()
                    assertTrue((testPFR in 0.0..1.0) || testPFR.isNaN())
                    assertEquals(Double.NaN, row[8].toDouble())
                    assertEquals(Double.NaN, row[9].toDouble())
                    val key = Experiment.Key(row[0], row[1].toInt(), row[2].toInt(), row[3].toInt())
                    with(File(artifacts, key.modelFileName)) {
                        assertTrue(exists())
                        assertTrue(isFile)
                        assertTrue { FileUtils.sizeOf(this) > 0 }
                    }
                    if (!trainPFR.isNaN())
                        with(File(artifacts, key.trainFileName)) {
                            assertTrue(exists())
                            assertTrue(isFile)
                            assertTrue { FileUtils.sizeOf(this) > 0 }
                        }
                    if (!testPFR.isNaN())
                        with(File(artifacts, key.testFileName)) {
                            assertTrue(exists())
                            assertTrue(isFile)
                            assertTrue { FileUtils.sizeOf(this) > 0 }
                        }
                }
            }
        } finally {
            temp.delete()
            artifacts.deleteRecursively()
        }
    }

//    @Ignore("Not a test")
    @Test
    fun `BPIC15_2`() {
        val configText = """
            {
                    "logs": [
                      "../xes-logs/BPIC15_2.xes.gz"
                    ],
                    "splitSeed": 3737844653,
                    "sampleSeed": 12648430,
                    "cvSeed": 4276993775,
                    "keval": 5,
                    "kfit": 5,
                    "csv": "output-20201210.csv",
                    "knownNamesThreshold": 100,
                    "missThreshold": 10,
                    "maxQueueSize": 100,
                    "mode": "DRIFT",
                    "batchSizes": [25],
                    "minDependency": [0],
                    "measures": ["TRAIN_PFR", "TEST_PFR"]
            }

        """.trimIndent()
        val config = Json.Default.decodeFromString(Experiment.Config.serializer(), configText)
        println(config)
        Experiment().drift(config)
    }

    @Test
    fun `BPIC15_2 window 50 sublog 1 trace 7 ArithmeticException`() {
        val logfile = File("../xes-logs/BPIC15_2.xes.gz")
        val splitSeed = 3737844653L
        val sampleSeed = 12648430L
        val keval = 5
        val knownNamesThreshold = 100
        val missThreshold = 10
        val windowSize = 50
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
        val hm = OnlineMiner()
        val windowEnd = logs[0].size + 7
        val windowStart = windowEnd - windowSize + 1
        val trainLog = flatLog.subList(windowStart, windowEnd + 1)
        hm.processDiff(Log(trainLog.asSequence()), Log(emptySequence()))
    }

    @Test
    fun `Sepsis_Cases-Event_Log`() {
        (LoggerFactory.getLogger("processm") as ch.qos.logback.classic.Logger).level = Level.INFO
        val temp = createTempFile()
        temp.deleteOnExit()
        val configText = """
            {
                    "logs": [
                      "../xes-logs/Sepsis_Cases-Event_Log.xes.gz"
                    ],
                    "splitSeed": 3737844653,
                    "sampleSeed": 12648430,
                    "cvSeed": 4276993775,
                    "keval": 5,
                    "kfit": 5,
                    "csv": "${temp.absolutePath}",
                    "knownNamesThreshold": 100,
                    "missThreshold": 10,
                    "maxQueueSize": 100,
                    "mode": "DRIFT",
                    "batchSizes": [25],
                    "minDependency": [0], 
                    "measures": []
            }

        """.trimIndent()
        val config = Json.Default.decodeFromString(Experiment.Config.serializer(), configText)
        println(config)
        Experiment().drift(config)
    }

    @Test
    fun `performance`() {
        (LoggerFactory.getLogger("processm") as ch.qos.logback.classic.Logger).level = Level.INFO
//        val temp = createTempFile()
//        temp.deleteOnExit()
        val temp = File("/tmp/output.csv")
        val configText = """
            {
                    "logs": [
                      "../xes-logs/CoSeLoG_WABO_2.xes.gz"
                    ],
                    "splitSeed": 3737844653,
                    "sampleSeed": 12648430,
                    "cvSeed": 4276993775,
                    "keval": 5,
                    "kfit": 5,
                    "csv": "${temp.absolutePath}",
                    "knownNamesThreshold": 100,
                    "missThreshold": 10,
                    "maxQueueSize": 100,
                    "mode": "PERFORMANCE",
                    "batchSizes": [100],
                    "minDependency": [0], 
                    "measures": []
            }

        """.trimIndent()
        val config = Json.Default.decodeFromString(Experiment.Config.serializer(), configText)
        println(config)
        Experiment().performance(config)
    }
}