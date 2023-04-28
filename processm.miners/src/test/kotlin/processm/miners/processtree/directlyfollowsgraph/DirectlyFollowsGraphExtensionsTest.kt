package processm.miners.processtree.directlyfollowsgraph

import processm.core.DBTestHelper
import processm.core.log.hierarchical.DBHierarchicalXESInputStream
import processm.core.models.dfg.DirectlyFollowsGraph
import processm.core.persistence.connection.DBCache
import processm.core.querylanguage.Query
import processm.dbmodels.models.load
import processm.dbmodels.models.store
import kotlin.test.Test
import kotlin.test.assertEquals

class DirectlyFollowsGraphExtensionsTest {

    @Test
    fun storeAndLoad() {
        val log =
            DBHierarchicalXESInputStream(DBTestHelper.dbName, Query("where l:id=${DBTestHelper.JournalReviewExtra}"))
        val dfg1 = DirectlyFollowsGraph().apply { discover(log) }
        val dfg1Id = dfg1.store(DBCache.get(DBTestHelper.dbName).database)
        val dfg2 = DirectlyFollowsGraph.load(DBCache.get(DBTestHelper.dbName).database, dfg1Id)

        assertEquals(dfg1.graph, dfg2.graph)
        assertEquals(dfg1.initialActivities, dfg2.initialActivities)
        assertEquals(dfg1.finalActivities, dfg2.finalActivities)
        assertEquals(dfg1, dfg2)
    }
}
