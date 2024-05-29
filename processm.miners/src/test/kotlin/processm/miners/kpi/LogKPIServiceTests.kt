package processm.miners.kpi

import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import processm.core.DBTestHelper
import processm.core.communication.Producer
import processm.core.esb.Artemis
import processm.core.esb.ServiceStatus
import processm.core.persistence.connection.transactionMain
import processm.dbmodels.models.*
import java.util.*
import kotlin.test.*

class LogKPIServiceTests {
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

    fun createKPIComponent(_query: String = "select count(l:name) where l:name='JournalReview'") {
        transactionMain {
            WorkspaceComponent.new {
                name = "test-kpi"
                componentType = ComponentTypeDto.Kpi
                dataStoreId = dataStore
                query = _query
                workspace = Workspace.all().firstOrNull() ?: Workspace.new { name = "test-workspace" }
            }
        }.triggerEvent(Producer(), WorkspaceComponentEventType.ComponentCreatedOrUpdated)
    }

    @AfterTest
    fun deleteKPIComponent() {
        transactionMain {
            WorkspaceComponents.deleteWhere {
                (WorkspaceComponents.name eq "test-kpi") and (WorkspaceComponents.dataStoreId eq dataStore)
            }
        }
    }

    @Test
    fun `create KPI component then run service`() {
        createKPIComponent()
        val logKpiService = LogKPIService()
        try {
            logKpiService.register()
            logKpiService.start()
            assertEquals(ServiceStatus.Started, logKpiService.status)

            Thread.sleep(1000L) // wait for calculation
        } finally {
            logKpiService.stop()
        }

        transactionMain {
            val component = WorkspaceComponent.find {
                WorkspaceComponents.dataStoreId eq dataStore
            }.first()

            val kpi = component.data?.toDouble()
            assertEquals(1.0, kpi)
            assertNotNull(component.dataLastModified)
            assertNull(component.lastError)
        }
    }

    @Test
    fun `run service then create KPI component`() {
        val logKpiService = LogKPIService()
        try {
            logKpiService.register()
            logKpiService.start()
            assertEquals(ServiceStatus.Started, logKpiService.status)

            createKPIComponent()

            Thread.sleep(1000L) // wait for calculation
        } finally {
            logKpiService.stop()
        }

        transactionMain {
            val component = WorkspaceComponent.find {
                WorkspaceComponents.dataStoreId eq dataStore
            }.first()

            val kpi = component.data?.toDouble()
            assertEquals(1.0, kpi)
            assertNotNull(component.dataLastModified)
            assertNull(component.lastError)
        }
    }

    @Test
    fun `create invalid KPI`() {
        createKPIComponent("just invalid query")
        val logKpiService = LogKPIService()
        try {
            logKpiService.register()
            logKpiService.start()
            assertEquals(ServiceStatus.Started, logKpiService.status)

            Thread.sleep(1000L) // wait for calculation
        } finally {
            logKpiService.stop()
        }

        transactionMain {
            val component = WorkspaceComponent.find {
                WorkspaceComponents.dataStoreId eq dataStore
            }.first()

            assertNull(component.data)
            assertNull(component.dataLastModified)
            assertNotNull(component.lastError)
            assertTrue("Line 1 position 0: mismatched input 'just'" in component.lastError!!, component.lastError)
        }
    }

    @Test
    fun `create invalid KPI then fix it`() {
        createKPIComponent("just invalid query")
        val logKpiService = LogKPIService()
        try {
            logKpiService.register()
            logKpiService.start()
            assertEquals(ServiceStatus.Started, logKpiService.status)

            Thread.sleep(1000L) // wait for calculation

            transactionMain {
                val component = WorkspaceComponent.find {
                    (WorkspaceComponents.name eq "test-kpi") and (WorkspaceComponents.dataStoreId eq dataStore)
                }.first()
                component.query = "select count(^t:name) where l:name='JournalReview'"
                component
            }.triggerEvent(Producer(), WorkspaceComponentEventType.ComponentCreatedOrUpdated)


            Thread.sleep(1000L) // wait for calculation
        } finally {
            logKpiService.stop()
        }

        transactionMain {
            val component = WorkspaceComponent.find {
                WorkspaceComponents.dataStoreId eq dataStore
            }.first()

            val kpi = component.data?.toDouble()
            assertEquals(101.0, kpi)
            assertNotNull(component.dataLastModified)
            assertNull(component.lastError)
        }
    }
}
