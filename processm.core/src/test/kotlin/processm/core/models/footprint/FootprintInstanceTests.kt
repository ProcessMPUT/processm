package processm.core.models.footprint

import processm.core.models.commons.Activity
import kotlin.test.Test
import kotlin.test.assertEquals

class FootprintInstanceTests {
    fun FootprintInstance.expecting(vararg activities: FootprintActivity): Map<Activity, FootprintActivityExecution> {
        assertEquals(activities.toSet(), this.availableActivities.toSet())
        return availableActivityExecutions.associateBy { it.activity }
    }

    @Test
    fun `PM book Table 6 2`() {
        val a = FootprintActivity("a")
        val b = FootprintActivity("b")
        val c = FootprintActivity("c")
        val d = FootprintActivity("d")
        val e = FootprintActivity("e")
        val f = FootprintActivity("f")

        val footprint = footprint {
            a `→` b
            a `→` c
            b `∥` c
            b `→` d
            b `→` e
            b `←` f
            c `→` d
            c `→` e
            c `←` f
            e `→` f
        }

        with(footprint.createInstance()) {
            expecting(a)[a]!!.execute()
            expecting(b, c)[c]!!.execute()
            expecting(b, d, e)[d]!!.execute()
            expecting()
        }
    }
}
