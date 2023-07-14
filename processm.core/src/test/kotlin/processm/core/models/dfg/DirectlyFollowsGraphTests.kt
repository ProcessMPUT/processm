package processm.core.models.dfg

import processm.core.DBTestHelper
import processm.core.log.hierarchical.DBHierarchicalXESInputStream
import processm.core.querylanguage.Query
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DirectlyFollowsGraphTests {
    @Test
    fun `Journal review complete only`() {
        val log = DBHierarchicalXESInputStream(
            DBTestHelper.dbName,
            Query("where l:id=${DBTestHelper.JournalReviewExtra} and transition='complete'")
        )

        val dfg = DirectlyFollowsGraph()
        dfg.discover(log)

        val a = dfg.activities.associateBy { it.name }
        val invite = a["invite reviewers"]!!
        val getReview1 = a["get review 1"]!!
        val getReview2 = a["get review 2"]!!
        val getReview3 = a["get review 3"]!!
        val getReviewX = a["get review X"]!!
        val timeout1 = a["time-out 1"]!!
        val timeout2 = a["time-out 2"]!!
        val timeout3 = a["time-out 3"]!!
        val timeoutX = a["time-out X"]!!
        val collect = a["collect reviews"]!!
        val decide = a["decide"]!!
        val inviteAdd = a["invite additional reviewer"]!!
        val accept = a["accept"]!!
        val reject = a["reject"]!!
        assertTrue { dfg.graph[invite, getReview1]!!.cardinality > 0 }
        assertTrue { dfg.graph[invite, getReview2]!!.cardinality > 0 }
        assertTrue { dfg.graph[invite, getReview3]!!.cardinality > 0 }
        assertNull(dfg.graph[invite, getReviewX])
        assertTrue { dfg.graph[invite, timeout1]!!.cardinality > 0 }
        assertTrue { dfg.graph[invite, timeout2]!!.cardinality > 0 }
        assertTrue { dfg.graph[invite, timeout3]!!.cardinality > 0 }
        assertNull(dfg.graph[invite, timeoutX])
        assertNull(dfg.graph[getReview1, timeout1])
        assertTrue { dfg.graph[getReview1, timeout2]!!.cardinality > 0 }
        assertTrue { dfg.graph[getReview1, timeout3]!!.cardinality > 0 }
        assertTrue { dfg.graph[getReview2, timeout1]!!.cardinality > 0 }
        assertNull(dfg.graph[getReview2, timeout2])
        assertTrue { dfg.graph[getReview2, timeout3]!!.cardinality > 0 }
        assertTrue { dfg.graph[getReview3, timeout1]!!.cardinality > 0 }
        assertTrue { dfg.graph[getReview3, timeout2]!!.cardinality > 0 }
        assertNull(dfg.graph[getReview3, timeout3])
        assertTrue { dfg.graph[getReview1, collect]!!.cardinality > 0 }
        assertTrue { dfg.graph[getReview2, collect]!!.cardinality > 0 }
        assertTrue { dfg.graph[getReview3, collect]!!.cardinality > 0 }
        assertTrue { dfg.graph[timeout1, collect]!!.cardinality > 0 }
        assertTrue { dfg.graph[timeout2, collect]!!.cardinality > 0 }
        assertTrue { dfg.graph[timeout3, collect]!!.cardinality > 0 }
        assertEquals(100, dfg.graph[collect, decide]!!.cardinality)
        assertTrue { dfg.graph[decide, accept]!!.cardinality > 0 }
        assertTrue { dfg.graph[decide, reject]!!.cardinality > 0 }
        assertTrue { dfg.graph[decide, inviteAdd]!!.cardinality > 0 }
        assertTrue { dfg.graph[inviteAdd, getReviewX]!!.cardinality > 0 }
        assertTrue { dfg.graph[inviteAdd, timeoutX]!!.cardinality > 0 }
        assertTrue { dfg.graph[getReviewX, accept]!!.cardinality > 0 }
        assertTrue { dfg.graph[getReviewX, reject]!!.cardinality > 0 }
        assertTrue { dfg.graph[getReviewX, inviteAdd]!!.cardinality > 0 }
        assertTrue { dfg.graph[timeoutX, inviteAdd]!!.cardinality > 0 }
    }
}
