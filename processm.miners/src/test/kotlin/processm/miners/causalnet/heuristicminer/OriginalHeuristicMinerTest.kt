package processm.miners.causalnet.heuristicminer

import processm.core.DBTestHelper
import processm.core.log.Helpers
import processm.core.log.hierarchical.DBHierarchicalXESInputStream
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.causalnet
import processm.core.querylanguage.Query
import processm.helpers.mapToSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * HM paper = https://pure.tue.nl/ws/portalfiles/portal/2388011/615595.pdf
 */
class OriginalHeuristicMinerTest {

    @Test
    fun `HM paper Fig 2`() {
        val log = Helpers.logFromString(
            """
            a b c d
            a b c d
            a b c d
            a b c d
            a b c d
            a b c d
            a b c d
            a b c d
            a b c d
            a c b d
            a c b d
            a c b d
            a c b d
            a c b d
            a c b d
            a c b d
            a c b d
            a c b d
            a e d
            a e d
            a e d
            a e d
            a e d
            a e d
            a e d
            a e d
            a e d
            a b c e d
            a e c b d
            a d
        """.trimIndent()
        )
        val miner = OriginalHeuristicMiner(dependencyThreshold = .9)
        miner.processLog(log)
        with(miner.result) {
            // Fig 2 and Table 2 in https://pure.tue.nl/ws/portalfiles/portal/2388011/615595.pdf
            assertEquals(5, activities.size)
            assertTrue { activities.none { it.isSilent } }
            assertEquals(setOf("a", "b", "c", "d", "e"), activities.mapToSet { it.name })
            assertEquals("a", start.name)
            assertEquals("d", end.name)
            val a = start
            val b = activities.single { it.name == "b" }
            val c = activities.single { it.name == "c" }
            val d = end
            val e = activities.single { it.name == "e" }
            with(dependencies) {
                assertEquals(6, size)
                assertTrue { any { it.source == a && it.target == b } }
                assertTrue { any { it.source == a && it.target == c } }
                assertTrue { any { it.source == a && it.target == e } }
                assertTrue { any { it.source == b && it.target == d } }
                assertTrue { any { it.source == c && it.target == d } }
                assertTrue { any { it.source == e && it.target == d } }
            }
        }
    }


    @Test
    fun `HM paper Table 2`() {
        val log = Helpers.logFromString(
            """
            a b c d
            a b c d
            a c b d
            a c b d
            a e d
        """.trimIndent()
        )
        val miner = OriginalHeuristicMiner()
        miner.processLog(log)
        with(miner.result) {
            // Fig 2 and Table 2 in https://pure.tue.nl/ws/portalfiles/portal/2388011/615595.pdf
            assertEquals(5, activities.size)
            assertTrue { activities.none { it.isSilent } }
            assertEquals(setOf("a", "b", "c", "d", "e"), activities.mapToSet { it.name })
            assertEquals("a", start.name)
            assertEquals("d", end.name)
            val a = start
            val b = activities.single { it.name == "b" }
            val c = activities.single { it.name == "c" }
            val d = end
            val e = activities.single { it.name == "e" }
            with(dependencies) {
                assertEquals(6, size)
                assertTrue { any { it.source == a && it.target == b } }
                assertTrue { any { it.source == a && it.target == c } }
                assertTrue { any { it.source == a && it.target == e } }
                assertTrue { any { it.source == b && it.target == d } }
                assertTrue { any { it.source == c && it.target == d } }
                assertTrue { any { it.source == e && it.target == d } }
            }
            // Paper: (b XOR e) ^ (c XOR e), which is equivalent to (b ^ c) XOR e, as stated here
            assertEquals(setOf(setOf(e), setOf(b, c)), splits[a]?.mapToSet { it.targets })
            assertEquals(setOf(setOf(e), setOf(b, c)), joins[d]?.mapToSet { it.sources })
        }
    }

    @Test
    fun `non-unique start is correctly shielded`() {
        val expected = causalnet {
            val a = Node("a")
            val b = Node("b")
            val c = Node("c")
            end = b
            start splits a or c
            a splits b
            c splits b
            start joins a
            start joins c
            a or c join b
        }
        val log = Helpers.logFromString(
            """
            a b
            c b
        """.trimIndent()
        )
        val hm = OriginalHeuristicMiner(.9)
        hm.processLog(log)
        assertTrue { expected.structurallyEquals(hm.result) }
    }

    @Test
    fun `looping start is correctly shielded`() {
        val log = Helpers.logFromString(
            """
            a a a a b
            a a a a a a a a b
        """.trimIndent()
        )
        val hm = OriginalHeuristicMiner()
        hm.processLog(log)
        assertTrue { hm.result.start.isSilent }
        assertFalse { hm.result.end.isSilent }
    }

    @Test
    fun `looping end is correctly shielded`() {
        val log = Helpers.logFromString(
            """
            b a a a a
            b a a a a a a a a
        """.trimIndent()
        )
        val hm = OriginalHeuristicMiner()
        hm.processLog(log)
        assertFalse { hm.result.start.isSilent }
        assertTrue { hm.result.end.isSilent }
    }

    @Test
    fun `JournalReviewExtra with threshold=0_9`() {
        val expectedModel = causalnet {
            val ir = Node("invite reviewers")
            val gr1 = Node("get review 1")
            val gr2 = Node("get review 2")
            val gr3 = Node("get review 3")
            val to1 = Node("time-out 1")
            val to2 = Node("time-out 2")
            val to3 = Node("time-out 3")
            val cr = Node("collect reviews")
            val d = Node("decide")
            val iar = Node("invite additional reviewer")
            val grX = Node("get review X")
            val toX = Node("time-out X")
            val a = Node("accept")
            val r = Node("reject")
            start = ir
            ir splits gr1 + gr2 + gr3 or gr1 + gr2 + to3 or gr1 + to2 + gr3 or gr1 + to2 + to3 or to1 + gr2 + gr3 or to1 + gr2 + to3 or to1 + to2 + gr3 or to1 + to2 + to3
            ir joins gr1
            ir joins gr2
            ir joins gr3
            ir joins to1
            ir joins to2
            ir joins to3
            gr1 splits cr
            gr2 splits cr
            gr3 splits cr
            to1 splits cr
            to2 splits cr
            to3 splits cr
            gr1 + gr2 + gr3 or gr1 + gr2 + to3 or gr1 + to2 + gr3 or gr1 + to2 + to3 or to1 + gr2 + gr3 or to1 + gr2 + to3 or to1 + to2 + gr3 or to1 + to2 + to3 join cr
            cr splits d
            cr joins d
            d splits iar
            grX or toX or d join iar
            iar splits grX or toX
            iar joins grX
            iar joins toX
            toX splits iar
            grX splits iar or a or r
            grX joins a
            grX joins r
            a splits end
            r splits end
            a or r join end
        }
        val log = DBHierarchicalXESInputStream(
            DBTestHelper.dbName,
            Query("select e:concept:name where l:identity:id=${DBTestHelper.JournalReviewExtra} and e:lifecycle:transition=\"complete\"")
        )
        val hm = OriginalHeuristicMiner(.9, andThreshold = .1)
        hm.processLog(log)
        assertTrue { expectedModel.structurallyEquals(hm.result) }
    }
}