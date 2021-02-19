package processm.experimental.onlinehmpaper

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import processm.core.log.hierarchical.InMemoryXESProcessing
import kotlin.test.*


@InMemoryXESProcessing
@ExperimentalStdlibApi
class ExperimentTest {

    @Test
    fun test() {
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
                    "csv": "/dev/null",
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
    fun test2() {
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
                    "csv": "/dev/null",
                    "knownNamesThreshold": 100,
                    "missThreshold": 10,
                    "maxQueueSize": 100,
                    "mode": "DRIFT",
                    "batchSizes": [25],
                    "minDependency": [0]
            }

        """.trimIndent()
        val config = Json.Default.decodeFromString(Experiment.Config.serializer(), configText)
        println(config)
        Experiment().drift(config)
    }

    @Test
    fun test3() {
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
                    "measures": ["TRAIN_FITNESS", "TEST_FITNESS"]
            }

        """.trimIndent()
        // , 50, 75, 100
        val config = Json.Default.decodeFromString(Experiment.Config.serializer(), configText)
        println(config)
        Experiment().drift(config)
    }
}