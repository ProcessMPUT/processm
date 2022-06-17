package processm.core.models.footprint

import processm.core.models.commons.ControlStructureType.OtherSplit
import kotlin.test.Test
import kotlin.test.assertEquals

class FootprintTests {
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

        assertEquals(setOf(a, b, c, d, e, f), footprint.activities.toSet())
        assertEquals(setOf(a), footprint.startActivities.toSet())
        assertEquals(setOf(d), footprint.endActivities.toSet())

        assertEquals(Order.NoOrder, footprint.matrix[a, a])
        assertEquals(Order.NoOrder, footprint.matrix[a, d])
        assertEquals(Order.NoOrder, footprint.matrix[a, e])
        assertEquals(Order.NoOrder, footprint.matrix[a, f])
        assertEquals(Order.NoOrder, footprint.matrix[b, b])
        assertEquals(Order.NoOrder, footprint.matrix[c, c])
        assertEquals(Order.NoOrder, footprint.matrix[d, a])
        assertEquals(Order.NoOrder, footprint.matrix[d, d])
        assertEquals(Order.NoOrder, footprint.matrix[d, e])
        assertEquals(Order.NoOrder, footprint.matrix[d, f])
        assertEquals(Order.NoOrder, footprint.matrix[e, a])
        assertEquals(Order.NoOrder, footprint.matrix[e, d])
        assertEquals(Order.NoOrder, footprint.matrix[e, e])
        assertEquals(Order.NoOrder, footprint.matrix[f, a])
        assertEquals(Order.NoOrder, footprint.matrix[f, d])
        assertEquals(Order.NoOrder, footprint.matrix[f, f])

        assertEquals(Order.FollowedBy, footprint.matrix[a, b])
        assertEquals(Order.FollowedBy, footprint.matrix[a, c])
        assertEquals(Order.FollowedBy, footprint.matrix[b, d])
        assertEquals(Order.FollowedBy, footprint.matrix[b, e])
        assertEquals(Order.FollowedBy, footprint.matrix[c, d])
        assertEquals(Order.FollowedBy, footprint.matrix[c, e])
        assertEquals(Order.FollowedBy, footprint.matrix[e, f])
        assertEquals(Order.FollowedBy, footprint.matrix[f, b])
        assertEquals(Order.FollowedBy, footprint.matrix[f, c])

        assertEquals(Order.PrecededBy, footprint.matrix[b, a])
        assertEquals(Order.PrecededBy, footprint.matrix[b, f])
        assertEquals(Order.PrecededBy, footprint.matrix[c, a])
        assertEquals(Order.PrecededBy, footprint.matrix[c, f])
        assertEquals(Order.PrecededBy, footprint.matrix[d, b])
        assertEquals(Order.PrecededBy, footprint.matrix[d, c])
        assertEquals(Order.PrecededBy, footprint.matrix[e, b])
        assertEquals(Order.PrecededBy, footprint.matrix[e, c])
        assertEquals(Order.PrecededBy, footprint.matrix[f, e])

        assertEquals(Order.Parallel, footprint.matrix[b, c])
        assertEquals(Order.Parallel, footprint.matrix[c, b])
    }

    @Test
    fun controlStructures() {
        val a = FootprintActivity("a")
        val b = FootprintActivity("b")
        val c = FootprintActivity("c")
        val d = FootprintActivity("d")
        val e = FootprintActivity("e")
        val f = FootprintActivity("f")

        val model = footprint {
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

        val otherSplits = model.controlStructures
            .filter { it.type == OtherSplit }
            .sortedBy { it.previousActivity.name + it.nextActivity.name }
            .toList()
        assertEquals(11, otherSplits.size)
        assertEquals(a, otherSplits[0].previousActivity)
        assertEquals(b, otherSplits[0].nextActivity)
        assertEquals(a, otherSplits[1].previousActivity)
        assertEquals(c, otherSplits[1].nextActivity)
        assertEquals(b, otherSplits[2].previousActivity)
        assertEquals(c, otherSplits[2].nextActivity)
        assertEquals(b, otherSplits[3].previousActivity)
        assertEquals(d, otherSplits[3].nextActivity)
        assertEquals(b, otherSplits[4].previousActivity)
        assertEquals(e, otherSplits[4].nextActivity)
        assertEquals(c, otherSplits[5].previousActivity)
        assertEquals(b, otherSplits[5].nextActivity)
        assertEquals(c, otherSplits[6].previousActivity)
        assertEquals(d, otherSplits[6].nextActivity)
        assertEquals(c, otherSplits[7].previousActivity)
        assertEquals(e, otherSplits[7].nextActivity)
        assertEquals(e, otherSplits[8].previousActivity)
        assertEquals(f, otherSplits[8].nextActivity)
        assertEquals(f, otherSplits[9].previousActivity)
        assertEquals(b, otherSplits[9].nextActivity)
        assertEquals(f, otherSplits[10].previousActivity)
        assertEquals(c, otherSplits[10].nextActivity)
        assertEquals(0, model.controlStructures.count { it.type != OtherSplit })
    }
}
