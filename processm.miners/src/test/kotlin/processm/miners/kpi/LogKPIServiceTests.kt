package processm.miners.kpi

import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import processm.core.esb.Artemis
import processm.core.esb.ServiceStatus
import processm.core.log.DBXESOutputStream
import processm.core.log.XMLXESInputStream
import processm.core.log.attribute.IDAttr
import processm.core.persistence.connection.DBCache
import processm.dbmodels.models.ComponentTypeDto
import processm.dbmodels.models.Workspace
import processm.dbmodels.models.WorkspaceComponent
import processm.dbmodels.models.WorkspaceComponents
import processm.miners.triggerEvent
import java.util.*
import kotlin.test.*

class LogKPIServiceTests {
    companion object {

        val dataStore = UUID.randomUUID()
        val logUUID = UUID.randomUUID()
        val artemis = Artemis()

        @JvmStatic
        @BeforeAll
        fun setUp() {
            DBXESOutputStream::class.java.getResourceAsStream("/xes-logs/JournalReview-extra.xes").use { stream ->
                DBXESOutputStream(DBCache.get(dataStore.toString()).getConnection()).use { output ->
                    output.write(XMLXESInputStream(stream).map {
                        if (it is processm.core.log.Log)
                            processm.core.log.Log(
                                HashMap(it.attributes).apply { put("identity:id", IDAttr("identity:id", logUUID)) },
                                HashMap(it.extensions),
                                HashMap(it.traceGlobals),
                                HashMap(it.eventGlobals),
                                HashMap(it.traceClassifiers),
                                HashMap(it.eventClassifiers)
                            )
                        else
                            it
                    })
                }
            }

            artemis.register()
            artemis.start()
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            artemis.stop()
        }
    }

    fun createKPIComponent(_query: String = "select count(l:name)") {
        transaction(DBCache.getMainDBPool().database) {
            WorkspaceComponent.new {
                name = "test-kpi"
                componentType = ComponentTypeDto.Kpi
                dataStoreId = dataStore
                query = _query
                workspace = Workspace.all().firstOrNull() ?: Workspace.new { name = "test-workspace" }
            }
        }.triggerEvent()
    }

    @AfterTest
    fun deleteKPIComponent() {
        transaction(DBCache.getMainDBPool().database) {
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

        transaction(DBCache.getMainDBPool().database) {
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

        transaction(DBCache.getMainDBPool().database) {
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

        transaction(DBCache.getMainDBPool().database) {
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

            transaction(DBCache.getMainDBPool().database) {
                val component = WorkspaceComponent.find {
                    (WorkspaceComponents.name eq "test-kpi") and (WorkspaceComponents.dataStoreId eq dataStore)
                }.first()
                component.query = "select count(^t:name)"
                component
            }.triggerEvent()


            Thread.sleep(1000L) // wait for calculation
        } finally {
            logKpiService.stop()
        }

        transaction(DBCache.getMainDBPool().database) {
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
