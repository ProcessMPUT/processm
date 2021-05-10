package processm.core.models.footprint

import processm.core.log.Helpers.logFromString
import kotlin.test.Test
import kotlin.test.assertEquals

class XESToFootprintTests {
        @Test
        fun `PM book Table 6 2`() {
                val log = logFromString(
                        """
            a b c d
            a c b d
            a b c e f b c d
            a b c e f c b d
            a c b e f b c d
            a c b e f b c e f c b d
        """
                )

                val footprint = log.toFootprint()

                val a = FootprintActivity("a")
                val b = FootprintActivity("b")
                val c = FootprintActivity("c")
                val d = FootprintActivity("d")
                val e = FootprintActivity("e")
                val f = FootprintActivity("f")

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
}
