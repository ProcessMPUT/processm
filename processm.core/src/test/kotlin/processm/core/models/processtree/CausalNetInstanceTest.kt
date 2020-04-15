package processm.core.models.processtree

import processm.core.models.processtree.execution.ActivityExecution
import kotlin.test.Test
import kotlin.test.assertEquals

class CausalNetInstanceTest {

    fun ProcessTreeInstance.expecting(vararg what: ProcessTreeActivity): List<ActivityExecution> {
        val result = this.availableActivityExecutions.toList()
        assertEquals(what.toList(), this.availableActivities.toList())
        assertEquals(what.toList(), result.map { it.base })
        return result
    }

    @Test
    fun `×(⟲(a,b),⟲(c,d))`() {
        val a = ProcessTreeActivity("a")
        val b = ProcessTreeActivity("b")
        val c = ProcessTreeActivity("c")
        val d = ProcessTreeActivity("d")
        val l1 = RedoLoop(a, b)
        val l2 = RedoLoop(c, d)
        val top = Exclusive(l1, l2)
        val model = processTree { top }
        val instance = model.createInstance()
        with(instance) {
            expecting(a, c)[0].execute()
            expecting(EndLoopSilentActivity(l1), b)[1].execute()
            expecting(a)[0].execute()
            expecting(EndLoopSilentActivity(l1), b)[0].execute()
            expecting()
        }
        instance.resetExecution()
        with(instance) {
            expecting(a, c)[1].execute()
            expecting(EndLoopSilentActivity(l2), d)[1].execute()
            expecting(c)[0].execute()
            expecting(EndLoopSilentActivity(l2), d)[0].execute()
            expecting()
        }
    }
}