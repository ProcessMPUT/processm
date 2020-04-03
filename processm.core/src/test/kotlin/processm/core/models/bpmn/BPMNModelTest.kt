package processm.core.models.bpmn

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class BPMNModelTest {

    @Test
    fun `bpmnio A40-export`() {
        val model =
            BPMNModel.fromXML(File("src/test/resources/bpmn-miwg-test-suite/bpmn.io (Cawemo, Camunda Modeler) 1.12.0/A.4.0-export.bpmn").inputStream())
        assertEquals(
            setOf(
                "Task 1", "Task 2", "Task 3", "Task 4", "Task 5", "Task 6",
                "Expanded Sub-Process 1", "Expanded Sub-Process 2"
            ),
            model.activities.map { it.name }.toSet()
        )
        assertEquals(
            setOf("Task 1", "Task 3"),
            model.startActivities.map { it.name }.toSet()
        )
        assertEquals(
            setOf("Task 2", "Task 5", "Expanded Sub-Process 2"),
            model.endActivities.map { it.name }.toSet()
        )
    }
}
