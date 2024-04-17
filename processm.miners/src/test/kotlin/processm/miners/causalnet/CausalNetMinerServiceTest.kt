package processm.miners.causalnet

import io.mockk.*
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import processm.core.DBTestHelper
import processm.core.communication.Producer
import processm.core.esb.Artemis
import processm.core.log.hierarchical.LogInputStream
import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.CausalNets
import processm.core.models.causalnet.DBSerializer
import processm.core.models.processtree.ProcessTrees
import processm.core.persistence.connection.DBCache
import processm.core.persistence.connection.transactionMain
import processm.dbmodels.afterCommit
import processm.dbmodels.models.*
import processm.helpers.toUUID
import processm.miners.ALGORITHM_HEURISTIC_MINER
import processm.miners.ALGORITHM_INDUCTIVE_MINER
import processm.miners.causalnet.onlineminer.OnlineMiner
import processm.miners.processtree.inductiveminer.OnlineInductiveMiner
import java.lang.Thread.sleep
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CausalNetMinerServiceTest {
    companion object {
        private val producer = Producer()
        private var artemis = Artemis()
        private var service: CausalNetMinerService? = null

        @BeforeAll
        @JvmStatic
        fun startServices() {
            mockkConstructor(OnlineMiner::class, OnlineInductiveMiner::class)
            every { anyConstructed<OnlineMiner>().processLog(any<LogInputStream>()) } returns CausalNets.fig312
            every { anyConstructed<OnlineInductiveMiner>().processLog(any()) } returns ProcessTrees.fig727

            artemis.register()
            artemis.start()

            // CausalNetMinerService must be created after mocks are set
            service = CausalNetMinerService()
            service!!.register()
            service!!.start()
        }

        @AfterAll
        @JvmStatic
        fun stopServices() {
            service!!.stop()
            artemis!!.stop()

            unmockkAll()
        }
    }

    private fun createComponent(algo: String): WorkspaceComponent = transactionMain {
        val ws = Workspace.new {
            name = "Test workspace"
        }
        WorkspaceComponent.new {
            workspace = ws
            name = "c-net"
            componentType = ComponentTypeDto.CausalNet
            dataStoreId = DBTestHelper.dbName.toUUID()!!
            algorithm = algo
            query = "where l:id=${DBTestHelper.JournalReviewExtra}"

            afterCommit {
                triggerEvent(producer)
            }
        }
    }

    private fun deleteComponent(component: WorkspaceComponent) = transactionMain {
        component.deleted = true
        component.afterCommit {
            component.triggerEvent(producer, DELETE)
        }

        component.workspace.deleted = true
    }

    @ParameterizedTest
    @ValueSource(strings = arrayOf(ALGORITHM_HEURISTIC_MINER, ALGORITHM_INDUCTIVE_MINER))
    fun `discover c-net`(algorithm: String) {
        clearAllMocks(
            answers = false,
            recordedCalls = true,
            childMocks = false,
            regularMocks = false,
            objectMocks = false,
            staticMocks = false,
            constructorMocks = false
        )

        val component = createComponent(algorithm)
        try {

            var cnet: CausalNet? = null
            for (attempt in 1..20) {
                cnet = transactionMain {
                    component.refresh()
                    component.mostRecentData()?.let {
                        DBSerializer.fetch(DBCache.get(DBTestHelper.dbName).database, it.toInt())
                    }
                }

                if (cnet !== null)
                    break
                else
                    sleep(100)
            }

            assertNotNull(cnet, "Expecting a C-net to be created.")

        } finally {
            deleteComponent(component)
        }

        when (algorithm) {
            ALGORITHM_HEURISTIC_MINER -> verify(exactly = 1) { anyConstructed<OnlineMiner>().processLog(any<LogInputStream>()) }
            ALGORITHM_INDUCTIVE_MINER -> verify(exactly = 1) { anyConstructed<OnlineInductiveMiner>().processLog(any()) }
        }
    }

    @Test
    fun `delete c-net on component removal`() {
        val component = createComponent(ALGORITHM_HEURISTIC_MINER)

        var cnetId: Int? = null
        for (attempt in 1..20) {
            cnetId = transactionMain {
                component.refresh()
                component.mostRecentData()?.toInt()
            }

            if (cnetId !== null)
                break
            else
                sleep(100)
        }

        assertNotNull(cnetId, "Expecting a C-net to be created.")

        var comp: WorkspaceComponent? = component
        var cnet: CausalNet? = DBSerializer.fetch(DBCache.get(DBTestHelper.dbName).database, cnetId)

        // wait for c-net and component to be deleted
        deleteComponent(component)
        for (attempt in 1..20) {
            transactionMain {
                comp = WorkspaceComponent.findById(component.id)
                cnet = try {
                    DBSerializer.fetch(DBCache.get(DBTestHelper.dbName).database, cnetId)
                } catch (e: NoSuchElementException) {
                    null
                }
            }

            if (comp === null && cnet === null)
                break
            else
                sleep(100L)
        }

        assertNull(comp, "Expecting component ${component.id} to be deleted.")
        assertNull(cnet, "Expecting cnet ${cnetId} to be deleted.")
    }
}
