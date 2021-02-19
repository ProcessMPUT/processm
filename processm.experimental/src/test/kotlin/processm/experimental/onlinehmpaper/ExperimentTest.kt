package processm.experimental.onlinehmpaper

import kotlinx.serialization.json.Json
import processm.core.log.hierarchical.InMemoryXESProcessing
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@InMemoryXESProcessing
@ExperimentalStdlibApi
class ExperimentTest {

    @Test
    fun `CoSeLoG_WABO_2`() {
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
                    "measures": ["TRAIN_PFR", "TEST_PFR"]
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
        val temp = createTempFile()
        temp.deleteOnExit()
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
                    "measures": ["TRAIN_PFR", "TEST_PFR"]
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
                assertTrue ( row[1].toInt() in setOf(5, 10))
                assertTrue(row[4].toDouble() in setOf(Double.NaN, 1.0))
                assertEquals(Double.NaN, row[5].toDouble())
                assertEquals(Double.NaN, row[6].toDouble())
                val testPFR = row[7].toDouble()
                assertTrue ( (testPFR in 0.0..1.0) || testPFR.isNaN())
                assertEquals(Double.NaN, row[8].toDouble())
                assertEquals(Double.NaN, row[9].toDouble())
            }
        }
    }

    @Ignore("Not a test")
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
}