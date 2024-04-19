package processm.core.models.bpmn.converters

import processm.core.models.bpmn.BPMNGateway
import processm.core.models.commons.ControlStructureType
import processm.core.models.processtree.ProcessTree
import processm.helpers.mapToSet
import kotlin.test.Test
import kotlin.test.assertEquals

class ProcessTree2BPMNTest {

    @Test
    fun sequence() {
        with(ProcessTree.parse("→(A,B,C)").toBPMN()) {
            assertEquals(1, processes.size)
            with(processes[0].allActivities) {
                assertEquals(5, size)
                val start = single { it.isStart }
                val a = single { it.name == "A" }
                val b = single { it.name == "B" }
                val c = single { it.name == "C" }
                val end = single { it.isEnd }
                assertEquals(setOf(a), start.split.possibleOutcomes.single().activities)
                assertEquals(setOf(b), a.split.possibleOutcomes.single().activities)
                assertEquals(setOf(c), b.split.possibleOutcomes.single().activities)
                assertEquals(setOf(end), c.split.possibleOutcomes.single().activities)
            }
        }
    }

    @Test
    fun `sequence with silents`() {
        with(ProcessTree.parse("→(τ,A,τ,C,τ,τ)").toBPMN()) {
            assertEquals(1, processes.size)
            with(processes[0].allActivities) {
                assertEquals(4, size)
                val start = single { it.isStart }
                val a = single { it.name == "A" }
                val c = single { it.name == "C" }
                val end = single { it.isEnd }
                assertEquals(setOf(a), start.split.possibleOutcomes.single().activities)
                assertEquals(setOf(c), a.split.possibleOutcomes.single().activities)
                assertEquals(setOf(end), c.split.possibleOutcomes.single().activities)
            }
        }
    }

    @Test
    fun exclusive() {
        with(ProcessTree.parse("×(A,B,C)").toBPMN()) {
            assertEquals(1, processes.size)
            with(processes[0].allActivities) {
                assertEquals(7, size)
                val start = single { it.isStart }
                val split = single { it is BPMNGateway && it.type == ControlStructureType.XorSplit }
                val a = single { it.name == "A" }
                val b = single { it.name == "B" }
                val c = single { it.name == "C" }
                val join = single { it is BPMNGateway && it.type == ControlStructureType.XorJoin }
                val end = single { it.isEnd }
                assertEquals(
                    setOf(setOf(a), setOf(b), setOf(c)),
                    split.split.possibleOutcomes.mapToSet { it.activities })
                assertEquals(setOf(join), a.split.possibleOutcomes.single().activities)
                assertEquals(setOf(join), b.split.possibleOutcomes.single().activities)
                assertEquals(setOf(join), c.split.possibleOutcomes.single().activities)
                assertEquals(setOf(end), join.split.possibleOutcomes.single().activities)
                assertEquals(setOf(split), start.split.possibleOutcomes.single().activities)
            }
        }
    }

    @Test
    fun parallel() {
        with(ProcessTree.parse("∧(A,B,C)").toBPMN()) {
            assertEquals(1, processes.size)
            with(processes[0].allActivities) {
                assertEquals(7, size)
                val start = single { it.isStart }
                val split = single { it is BPMNGateway && it.type == ControlStructureType.AndSplit }
                val a = single { it.name == "A" }
                val b = single { it.name == "B" }
                val c = single { it.name == "C" }
                val join = single { it is BPMNGateway && it.type == ControlStructureType.AndJoin }
                val end = single { it.isEnd }
                assertEquals(setOf(a, b, c), split.split.possibleOutcomes.single().activities)
                assertEquals(setOf(join), a.split.possibleOutcomes.single().activities)
                assertEquals(setOf(join), b.split.possibleOutcomes.single().activities)
                assertEquals(setOf(join), c.split.possibleOutcomes.single().activities)
                assertEquals(setOf(end), join.split.possibleOutcomes.single().activities)
                assertEquals(setOf(split), start.split.possibleOutcomes.single().activities)
            }
        }
    }

    @Test
    fun redo() {
        with(ProcessTree.parse("⟲(A,B,C)").toBPMN()) {
            assertEquals(1, processes.size)
            with(processes[0].allActivities) {
                assertEquals(7, size)
                val start = single { it.isStart }
                val split = single { it is BPMNGateway && it.type == ControlStructureType.XorSplit }
                val a = single { it.name == "A" }
                val b = single { it.name == "B" }
                val c = single { it.name == "C" }
                val join = single { it is BPMNGateway && it.type == ControlStructureType.XorJoin }
                val end = single { it.isEnd }
                assertEquals(setOf(a), start.split.possibleOutcomes.single().activities)
                assertEquals(setOf(split), a.split.possibleOutcomes.single().activities)
                assertEquals(setOf(setOf(b), setOf(c)), split.split.possibleOutcomes.mapToSet { it.activities })
                assertEquals(setOf(join), b.split.possibleOutcomes.single().activities)
                assertEquals(setOf(join), c.split.possibleOutcomes.single().activities)
                assertEquals(setOf(setOf(a), setOf(end)), join.split.possibleOutcomes.mapToSet { it.activities })

            }
        }
    }
}