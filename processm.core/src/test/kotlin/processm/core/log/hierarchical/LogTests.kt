package processm.core.log.hierarchical

import processm.core.log.Event
import kotlin.test.Test
import kotlin.test.assertTrue

class LogTests {

    @Test
    fun toFlatSequenceTest() {
        val log = Log(sequenceOf(
            Trace(sequenceOf(
                Event().apply { conceptName = "A" },
                Event().apply { conceptName = "B" },
                Event().apply { conceptName = "C" }
            )).apply {
                conceptName = "T"
            },
            Trace(sequenceOf(
                Event().apply { conceptName = "D" },
                Event().apply { conceptName = "E" },
                Event().apply { conceptName = "F" }
            )).apply {
                conceptName = "U"
            }
        )).apply {
            conceptName = "L"
        }

        assertTrue(log.traces.first().toFlatSequence().withIndex().all { (index, element) ->
            element.conceptName!![0] == "TABC"[index]
        })

        assertTrue(log.traces.drop(1).first().toFlatSequence().withIndex().all { (index, element) ->
            element.conceptName!![0] == "UDEF"[index]
        })

        assertTrue(log.toFlatSequence().withIndex().all { (index, element) ->
            element.conceptName!![0] == "LTABCUDEF"[index]
        })

        val logs = sequenceOf(log, log, log)
        assertTrue(logs.toFlatSequence().withIndex().all { (index, element) ->
            element.conceptName!![0] == "LTABCUDEFLTABCUDEFLTABCUDEF"[index]
        })
    }
}