package processm.miners.processtree.directlyfollowsgraph

import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import processm.core.DBTestHelper
import processm.core.communication.Producer
import processm.core.esb.Artemis
import processm.core.esb.ServiceStatus
import processm.core.models.dfg.DirectlyFollowsGraph
import processm.core.persistence.connection.DBCache
import processm.core.persistence.connection.transactionMain
import processm.dbmodels.models.*
import java.util.*
import kotlin.test.*

class DirectlyFollowsGraphMinerServiceTest {

    companion object {

        val dataStore = UUID.fromString(DBTestHelper.dbName)

        @Suppress("unused") // make sure that the lazy field JournalReviewExtra is initialized
        val journal = DBTestHelper.JournalReviewExtra
        val artemis = Artemis()

        @JvmStatic
        @BeforeAll
        fun setUp() {
            artemis.register()
            artemis.start()
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            artemis.stop()
        }
    }

    private fun createDFGComponent(_query: String = "where l:id=$journal") {
        transactionMain {
            WorkspaceComponent.new {
                name = "test-dfg"
                componentType = ComponentTypeDto.DirectlyFollowsGraph
                dataStoreId = dataStore
                query = _query
                workspace = Workspace.all().firstOrNull() ?: Workspace.new { name = "test-workspace" }
            }
        }.triggerEvent(Producer())
    }

    @AfterTest
    fun deleteComponent(): Unit = transactionMain {
        WorkspaceComponents.deleteWhere {
            (WorkspaceComponents.name eq "test-dfg") and (WorkspaceComponents.dataStoreId eq dataStore)
        }
    }

    @Test
    fun `create DFG component then run service`() {
        createDFGComponent()
        val service = DirectlyFollowsGraphMinerService()
        try {
            service.register()
            service.start()
            assertEquals(ServiceStatus.Started, service.status)

            Thread.sleep(1000L) // wait for calculation
        } finally {
            service.stop()
        }

        transactionMain {
            val component = WorkspaceComponent.find {
                WorkspaceComponents.dataStoreId eq dataStore
            }.first()

            val dfg =
                DirectlyFollowsGraph.load(DBCache.get(dataStore.toString()).database, UUID.fromString(component.data!!))
            assertEquals(1, dfg.initialActivities.size)
            assertEquals(3, dfg.finalActivities.size)
            val invite = dfg.activities.first { it.name == "invite reviewers" }
            assertEquals("invite reviewers", invite.name)
            assertEquals(100, dfg.graph.getRow(invite)[invite]!!.cardinality)
            val getReview1 = dfg.activities.first { it.name == "get review 1" }
            val getReview2 = dfg.activities.first { it.name == "get review 2" }
            val getReview3 = dfg.activities.first { it.name == "get review 3" }
            val timeout1 = dfg.activities.first { it.name == "time-out 1" }
            val timeout2 = dfg.activities.first { it.name == "time-out 2" }
            val timeout3 = dfg.activities.first { it.name == "time-out 3" }
            assertEquals(100, dfg.graph.getRow(invite)[invite]!!.cardinality)
            assertEquals(15, dfg.graph.getRow(invite)[getReview1]!!.cardinality)
            assertEquals(11, dfg.graph.getRow(invite)[timeout1]!!.cardinality)
            assertEquals(31, dfg.graph.getRow(invite)[getReview2]!!.cardinality)
            assertEquals(13, dfg.graph.getRow(invite)[timeout2]!!.cardinality)
            assertEquals(17, dfg.graph.getRow(invite)[getReview3]!!.cardinality)
            assertEquals(13, dfg.graph.getRow(invite)[timeout3]!!.cardinality)
            assertNotNull(component.dataLastModified)
            assertNull(component.lastError)
        }
    }

    @Test
    fun `run service then create DFG component`() {
        val service = DirectlyFollowsGraphMinerService()
        try {
            service.register()
            service.start()
            assertEquals(ServiceStatus.Started, service.status)

            createDFGComponent()

            Thread.sleep(1000L) // wait for calculation
        } finally {
            service.stop()
        }

        transactionMain {
            val component = WorkspaceComponent.find {
                WorkspaceComponents.dataStoreId eq dataStore
            }.first()

            val dfg =
                DirectlyFollowsGraph.load(DBCache.get(dataStore.toString()).database, UUID.fromString(component.data!!))
            assertEquals(1, dfg.initialActivities.size)
            assertEquals(3, dfg.finalActivities.size)
            val invite = dfg.activities.first { it.name == "invite reviewers" }
            assertEquals("invite reviewers", invite.name)
            assertEquals(100, dfg.graph.getRow(invite)[invite]!!.cardinality)
            val getReview1 = dfg.activities.first { it.name == "get review 1" }
            val getReview2 = dfg.activities.first { it.name == "get review 2" }
            val getReview3 = dfg.activities.first { it.name == "get review 3" }
            val timeout1 = dfg.activities.first { it.name == "time-out 1" }
            val timeout2 = dfg.activities.first { it.name == "time-out 2" }
            val timeout3 = dfg.activities.first { it.name == "time-out 3" }
            assertEquals(100, dfg.graph.getRow(invite)[invite]!!.cardinality)
            assertEquals(15, dfg.graph.getRow(invite)[getReview1]!!.cardinality)
            assertEquals(11, dfg.graph.getRow(invite)[timeout1]!!.cardinality)
            assertEquals(31, dfg.graph.getRow(invite)[getReview2]!!.cardinality)
            assertEquals(13, dfg.graph.getRow(invite)[timeout2]!!.cardinality)
            assertEquals(17, dfg.graph.getRow(invite)[getReview3]!!.cardinality)
            assertEquals(13, dfg.graph.getRow(invite)[timeout3]!!.cardinality)
            assertNotNull(component.dataLastModified)
            assertNull(component.lastError)
        }
    }
}